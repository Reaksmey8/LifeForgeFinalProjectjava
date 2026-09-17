package com.lifeforge.service;

import com.lifeforge.config.AppConfig;
import com.lifeforge.dao.PasswordResetDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.model.PasswordResetToken;
import com.lifeforge.model.User;
import com.lifeforge.util.PasswordUtil;
import com.lifeforge.util.ValidationUtil;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-service password reset flow:
 * 1. User requests a reset using their username or registered email.
 * 2. System validates account existence, generates a 6-digit OTP valid for 5 minutes,
 *    stores it in an in-memory registry, and logs it to the terminal/console.
 * 3. User submits verification code and new password in a single direct step.
 * 4. Password is hashed and updated immediately in PostgreSQL.
 */
public class PasswordResetService {

    /** Default OTP expiration duration (5 minutes). */
    public static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    /** Generic confirmation shown for existing accounts. */
    public static final String GENERIC_CONFIRMATION =
            "Verification code sent (valid for 5 minutes).";

    private final PasswordResetDao resetDao;
    private final UserDao userDao;
    private final AuditLogService auditLogService;
    private final CodeDeliveryService codeDeliveryService;
    private final SecureRandom secureRandom = new SecureRandom();

    /** In-memory registry of active reset tokens keyed by user ID. */
    private final Map<Long, PasswordResetToken> tokenRegistry = new ConcurrentHashMap<>();

    // Transient in-memory copy of the issued code for development hints
    private String devCode;
    private Long devCodeUserId;

    public PasswordResetService(UserDao userDao,
                                AuditLogService auditLogService,
                                CodeDeliveryService codeDeliveryService) {
        this(null, userDao, auditLogService, codeDeliveryService);
    }

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

        public static RequestResult accepted(Long userId, String message) {
            return new RequestResult(true, message, userId, null);
        }

        public static RequestResult statused(Long userId, String status, String message) {
            return new RequestResult(true, message, userId, status);
        }

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
    // Self-Service Flow
    // ------------------------------------------------------------------

    /**
     * Step 1: Request reset code by username or registered email.
     */
    public RequestResult requestReset(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return RequestResult.rejected("Please enter your username or registered email.");
        }
        String input = usernameOrEmail.trim();
        try {
            Optional<User> found = userDao.findByEmailOrUsername(input);
            if (found.isEmpty()) {
                return RequestResult.rejected("No account found matching '" + input + "'.");
            }
            User user = found.get();
            if (user.isBlocked()) {
                return RequestResult.rejected("This account has been blocked. Please contact support.");
            }

            String code = issueCode(user);
            return RequestResult.accepted(user.getId(), "Verification code sent (valid for 5 minutes).");
        } catch (SQLException e) {
            return RequestResult.rejected("Password reset is temporarily unavailable. Please try again later.");
        }
    }

    /**
     * Resend a new verification code for the user.
     */
    public RequestResult resendCode(Long userId) {
        if (userId == null) {
            return RequestResult.rejected("Invalid reset session. Please start over.");
        }
        try {
            Optional<User> found = userDao.findById(userId);
            if (found.isEmpty()) {
                return RequestResult.rejected("No account found for this reset session.");
            }
            User user = found.get();
            if (user.isBlocked()) {
                return RequestResult.rejected("This account has been blocked. Please contact support.");
            }
            String code = issueCode(user);
            return RequestResult.accepted(userId, "New verification code sent (valid for 5 minutes).");
        } catch (SQLException e) {
            return RequestResult.rejected("Resending the code is temporarily unavailable. Please try again later.");
        }
    }

    /**
     * Validates a verification code without setting a new password.
     */
    public VerifyResult verifyCode(Long userId, String code) {
        if (userId == null || code == null) {
            return VerifyResult.fail("Invalid verification request.");
        }
        String candidate = code.trim();
        if (!candidate.matches("\\d{6}")) {
            return VerifyResult.fail("The verification code must be exactly 6 digits.");
        }
        PasswordResetToken token = tokenRegistry.get(userId);
        if (token == null) {
            return VerifyResult.fail("No active verification code found. Please request a new one.");
        }
        if (token.isExpired()) {
            tokenRegistry.remove(userId);
            return VerifyResult.fail("Verification code has expired (valid for 5 minutes). Please request a new code.");
        }
        if (!token.getCode().equals(candidate)) {
            return VerifyResult.fail("Incorrect verification code.");
        }
        return VerifyResult.ok();
    }

    /**
     * Combined verification and password reset in a single self-service step.
     */
    public ResetResult resetPassword(Long userId, String code, String newPassword, String confirmPassword) {
        if (userId == null) {
            return ResetResult.fail("This reset session is no longer valid. Please request a new code.");
        }
        if (code == null || code.isBlank()) {
            return ResetResult.fail("Verification code is required.");
        }
        String candidate = code.trim();
        if (!candidate.matches("\\d{6}")) {
            return ResetResult.fail("The verification code must be exactly 6 digits.");
        }
        PasswordResetToken token = tokenRegistry.get(userId);
        if (token == null) {
            return ResetResult.fail("This reset session is no longer valid. Please request a new code.");
        }
        if (token.isExpired()) {
            tokenRegistry.remove(userId);
            return ResetResult.fail("Verification code has expired (valid for 5 minutes). Please request a new code.");
        }
        if (!token.getCode().equals(candidate)) {
            return ResetResult.fail("Incorrect verification code.");
        }

        String passwordCheck = ValidationUtil.validatePassword(newPassword);
        if (passwordCheck != null) {
            return ResetResult.fail(passwordCheck);
        }
        String confirmCheck = ValidationUtil.validatePasswordConfirmation(newPassword, confirmPassword);
        if (confirmCheck != null) {
            return ResetResult.fail(confirmCheck);
        }

        try {
            Optional<User> found = userDao.findById(userId);
            if (found.isEmpty()) {
                return ResetResult.fail("User account not found.");
            }
            userDao.updatePassword(userId, PasswordUtil.hash(newPassword));
            tokenRegistry.remove(userId);
            clearDevCode();
            auditLogService.log(userId, "PASSWORD_RESET_COMPLETED", "USER", userId,
                    "Self-service password reset completed successfully.");
            return ResetResult.ok();
        } catch (SQLException e) {
            return ResetResult.fail("Password reset failed due to a database error. Please try again.");
        }
    }

    /**
     * Overload for callers who already validated the code in a previous step.
     */
    public ResetResult resetPassword(Long userId, String newPassword, String confirmPassword) {
        if (userId == null) {
            return ResetResult.fail("This reset session is no longer valid. Please request a new code.");
        }
        PasswordResetToken token = tokenRegistry.get(userId);
        String code = token != null ? token.getCode() : "";
        return resetPassword(userId, code, newPassword, confirmPassword);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String issueCode(User user) {
        String code = generateCode();
        Instant expiry = Instant.now().plus(TOKEN_TTL);
        PasswordResetToken token = new PasswordResetToken(user.getId(), user.getUsername(), user.getEmail(), code, expiry);
        tokenRegistry.put(user.getId(), token);

        // Terminal / console logging
        LocalTime expiryTime = LocalTime.now().plusMinutes(5).truncatedTo(ChronoUnit.SECONDS);
        System.out.println();
        System.out.println("=======================================================");
        System.out.println("🔑 PASSWORD RESET VERIFICATION CODE: " + code);
        System.out.println("   Account  : " + (user.getUsername() != null ? user.getUsername() : user.getEmail()));
        System.out.println("   Validity : 5 minutes (expires at " + expiryTime + ")");
        System.out.println("=======================================================");
        System.out.println();

        // Deliver via delivery service if configured
        if (codeDeliveryService != null && user.getEmail() != null) {
            codeDeliveryService.deliver(user.getEmail(), code);
        }

        rememberDevCode(user.getId(), code);
        auditLogService.log(user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(),
                "Password reset verification code issued.");
        return code;
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }

    public Optional<PasswordResetToken> getToken(Long userId) {
        return Optional.ofNullable(tokenRegistry.get(userId));
    }

    public String getActiveCode(Long userId) {
        if (userId == null) {
            return null;
        }
        PasswordResetToken token = tokenRegistry.get(userId);
        if (token != null && !token.isExpired()) {
            return token.getCode();
        }
        return null;
    }

    public void setTokenForTest(Long userId, PasswordResetToken token) {
        if (token != null) {
            tokenRegistry.put(userId, token);
            rememberDevCode(userId, token.getCode());
        } else {
            tokenRegistry.remove(userId);
            clearDevCode();
        }
    }

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