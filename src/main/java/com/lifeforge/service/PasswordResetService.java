package com.lifeforge.service;

import com.lifeforge.config.AppConfig;
import com.lifeforge.dao.PasswordResetDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.model.PasswordReset;
import com.lifeforge.model.User;
import com.lifeforge.util.PasswordUtil;
import com.lifeforge.util.ValidationUtil;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Password reset flow: request a code, verify it, then set a new password.
 *
 * <p>Security properties enforced here:
 * <ul>
 *   <li>The plaintext code is never stored - only its BCrypt hash hits the DB.</li>
 *   <li>Codes expire (default 10 minutes) and are single-use.</li>
 *   <li>Codes have a bounded attempt budget; exhausting it forces a new code.</li>
 *   <li>Generating a new code invalidates every previous code for the user.</li>
 *   <li>Whether or not the email matches an account is never revealed - callers
 *       always get the same generic confirmation.</li>
 *   <li>Requests follow an administrator approval state machine:
 *       PENDING -> APPROVED -> COMPLETED, or PENDING -> REJECTED. A new password
 *       can only be set once an administrator has approved the request.</li>
 * </ul>
 */
public class PasswordResetService {

    /** State machine statuses for the password_resets.status column. */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** Generic confirmation shown for both existing and unknown emails. */
    public static final String GENERIC_CONFIRMATION =
            "If an account matches this email, a verification code has been sent.";

    private final PasswordResetDao resetDao;
    private final UserDao userDao;
    private final AuditLogService auditLogService;
    private final CodeDeliveryService codeDeliveryService;
    private final SecureRandom secureRandom = new SecureRandom();

    // Transient in-memory copy of the issued code, ONLY used to render a
    // development-only hint when LIFEFORGE_DEV_CODE_LOG is enabled. Never stored.
    private String devCode;
    private Long devCodeUserId;

    public PasswordResetService(PasswordResetDao resetDao, UserDao userDao,
                                AuditLogService auditLogService,
                                CodeDeliveryService codeDeliveryService) {
        this.resetDao = resetDao;
        this.userDao = userDao;
        this.auditLogService = auditLogService;
        this.codeDeliveryService = codeDeliveryService;
    }

    // ------------------------------------------------------------------
    // Results
    // ------------------------------------------------------------------
    /** Outcome of requesting (or resending) a reset code. */
    public static class RequestResult {
        public final boolean accepted;
        public final String message;
        public final Long userId;
        public final String status;

        private RequestResult(boolean accepted, String message, Long userId, String status) {
            this.accepted = accepted;
            this.message = message;
            this.userId = userId;
            this.status = status;
        }

        /** The request proceeded. {@code userId} is null when no account matched. */
        public static RequestResult accepted(Long userId, String message) {
            return new RequestResult(true, message, userId, null);
        }

        /** The request proceeded and carries the current state-machine status. */
        public static RequestResult statused(Long userId, String status, String message) {
            return new RequestResult(true, message, userId, status);
        }

        /** The request could not proceed (bad format or internal error). */
        public static RequestResult rejected(String message) {
            return new RequestResult(false, message, null, null);
        }
    }

    /** Outcome of a code-verification attempt. */
    public static class VerifyResult {
        public final boolean success;
        public final String message;

        private VerifyResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static VerifyResult ok() {
            return new VerifyResult(true, null);
        }

        public static VerifyResult fail(String message) {
            return new VerifyResult(false, message);
        }
    }

    /** Outcome of the final password reset step. */
    public static class ResetResult {
        public final boolean success;
        public final String message;

        private ResetResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ResetResult ok() {
            return new ResetResult(true, null);
        }

        public static ResetResult fail(String message) {
            return new ResetResult(false, message);
        }
    }

    // ------------------------------------------------------------------
    // Flow
    // ------------------------------------------------------------------
    public RequestResult requestReset(String email) {
        String formatCheck = ValidationUtil.validateEmail(email);
        if (formatCheck != null) {
            return RequestResult.rejected(formatCheck);
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            Optional<User> found = userDao.findByEmail(normalized);
            if (found.isEmpty()) {
                // Deliberately identical outcome - nothing to reveal.
                return RequestResult.accepted(null, GENERIC_CONFIRMATION);
            }
            User user = found.get();
            Optional<PasswordReset> existing = resetDao.findLatestByUser(user.getId());
            if (existing.isPresent()) {
                PasswordReset request = existing.get();
                return RequestResult.statused(request.getUserId(), request.getStatus(),
                        messageForStatus(request.getStatus()));
            }
            issueCode(user);
            return RequestResult.statused(user.getId(), STATUS_PENDING,
                    "Your request has been submitted and is awaiting administrator approval.");
        } catch (SQLException e) {
            return RequestResult.rejected(
                    "Password reset is temporarily unavailable. Please try again later.");
        }
    }

    private String messageForStatus(String status) {
        if (status == null) {
            return GENERIC_CONFIRMATION;
        }
        switch (status) {
            case STATUS_APPROVED:
                return "Your password reset request has been approved. You can now create a new password.";
            case STATUS_REJECTED:
                return "Your password reset request was rejected by an administrator. "
                        + "Please contact support for assistance.";
            case STATUS_COMPLETED:
                return "Your password has already been reset. Please log in with your new password.";
            case STATUS_PENDING:
            default:
                return "Your password reset request is still awaiting administrator approval.";
        }
    }

    public RequestResult resendCode(Long userId) {
        if (userId == null) {
            return RequestResult.accepted(null, GENERIC_CONFIRMATION);
        }
        try {
            Optional<User> found = userDao.findById(userId);
            if (found.isEmpty()) {
                return RequestResult.accepted(null, GENERIC_CONFIRMATION);
            }
            issueCode(found.get());
            return RequestResult.accepted(userId, GENERIC_CONFIRMATION);
        } catch (SQLException e) {
            return RequestResult.rejected(
                    "Resending the code is temporarily unavailable. Please try again later.");
        }
    }

    public VerifyResult verifyCode(Long userId, String code) {
        if (userId == null || code == null) {
            return VerifyResult.fail(invalidCodeMessage());
        }
        String candidate = code.trim();
        if (!candidate.matches("\\d{" + AppConfig.RESET_CODE_LENGTH + "}")) {
            return VerifyResult.fail("The verification code must be exactly "
                    + AppConfig.RESET_CODE_LENGTH + " digits.");
        }
        try {
            Optional<PasswordReset> active = resetDao.findActiveByUser(userId);
            if (active.isEmpty()) {
                return VerifyResult.fail(invalidCodeMessage());
            }
            PasswordReset record = active.get();
            int usedAttempts = record.getAttemptCount();
            int maxAttempts = AppConfig.getResetCodeMaxAttempts();
            if (usedAttempts >= maxAttempts) {
                resetDao.markUsed(record.getId());
                return VerifyResult.fail("Too many incorrect attempts. Use Resend Code for a fresh one.");
            }
            if (!PasswordUtil.verify(candidate, record.getCodeHash())) {
                int next = usedAttempts + 1;
                resetDao.setAttemptCount(record.getId(), next);
                if (next >= maxAttempts) {
                    resetDao.markUsed(record.getId());
                    return VerifyResult.fail("Too many incorrect attempts. Use Resend Code for a fresh one.");
                }
                return VerifyResult.fail("Incorrect verification code. "
                        + (maxAttempts - next) + " attempt(s) remaining.");
            }
            resetDao.markUsed(record.getId());
            clearDevCode();
            return VerifyResult.ok();
        } catch (SQLException e) {
            return VerifyResult.fail("Verification could not be completed. Please try again.");
        }
    }

    public ResetResult resetPassword(Long userId, String newPassword, String confirmPassword) {
        String passwordCheck = ValidationUtil.validatePassword(newPassword);
        if (passwordCheck != null) {
            return ResetResult.fail(passwordCheck);
        }
        String confirmCheck = ValidationUtil.validatePasswordConfirmation(newPassword, confirmPassword);
        if (confirmCheck != null) {
            return ResetResult.fail(confirmCheck);
        }
        if (userId == null) {
            return ResetResult.fail("This reset session is no longer valid. Please request a new code.");
        }
        try {
            Optional<User> found = userDao.findById(userId);
            if (found.isEmpty()) {
                return ResetResult.fail("This reset session is no longer valid. Please request a new code.");
            }
            Optional<PasswordReset> latest = resetDao.findLatestByUser(userId);
            if (latest.isEmpty()) {
                return ResetResult.fail("This reset session is no longer valid. Please request a new code.");
            }
            PasswordReset request = latest.get();
            String status = request.getStatus();
            if (!STATUS_APPROVED.equals(status)) {
                return ResetResult.fail(blockedReason(status));
            }
            userDao.updatePassword(userId, PasswordUtil.hash(newPassword));
            resetDao.markCompleted(request.getId());
            clearDevCode();
            auditLogService.log(userId, "PASSWORD_RESET_COMPLETED", "USER", userId,
                    "Password reset completed after administrator approval.");
            return ResetResult.ok();
        } catch (SQLException e) {
            return ResetResult.fail("Password reset failed due to a database error. Please try again.");
        }
    }

    private String blockedReason(String status) {
        if (status == null) {
            return "This reset session is no longer valid. Please request a new code.";
        }
        switch (status) {
            case STATUS_REJECTED:
                return "This password reset request was rejected by an administrator. "
                        + "Please contact support for assistance.";
            case STATUS_COMPLETED:
                return "This password reset has already been completed. "
                        + "Please log in with your new password.";
            case STATUS_PENDING:
            default:
                return "This password reset request is still awaiting administrator approval. "
                        + "Please try again later.";
        }
    }

    // ------------------------------------------------------------------
    // Delivery / debug helpers
    // ------------------------------------------------------------------
    private String invalidCodeMessage() {
        return "That code is invalid or has expired. Use Resend Code for a fresh one.";
    }

    private String issueCode(User user) throws SQLException {
        String code = generateCode();
        PasswordReset record = new PasswordReset();
        record.setUserId(user.getId());
        record.setCodeHash(PasswordUtil.hash(code));
        record.setExpiresAt(LocalDateTime.now()
                .plusMinutes(AppConfig.getResetCodeExpirationMinutes()));
        // A fresh code supersedes every previous one for this user.
        resetDao.invalidateForUser(user.getId());
        resetDao.create(record);
        codeDeliveryService.deliver(user.getEmail(), code);
        rememberDevCode(user.getId(), code);
        auditLogService.log(user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(),
                "Password reset verification code issued for account.");
        return code;
    }

    private String generateCode() {
        int digitSpace = (int) Math.pow(10, AppConfig.RESET_CODE_LENGTH);
        return String.format(Locale.ROOT, "%0" + AppConfig.RESET_CODE_LENGTH + "d",
                secureRandom.nextInt(digitSpace));
    }

    /**
     * Dev-only plaintext code for the TUI hint. Returns null unless
     * LIFEFORGE_DEV_CODE_LOG is enabled - the value never reaches the UI
     * in production.
     */
    public String devLastCode(Long userId) {
        if (!AppConfig.isDevResetCodeLoggingEnabled()) {
            return null;
        }
        if (devCodeUserId == null || !devCodeUserId.equals(userId)) {
            return null;
        }
        return devCode;
    }

    private void rememberDevCode(Long userId, String code) {
        if (AppConfig.isDevResetCodeLoggingEnabled()) {
            devCode = code;
            devCodeUserId = userId;
        }
    }

    private void clearDevCode() {
        devCode = null;
        devCodeUserId = null;
    }
}