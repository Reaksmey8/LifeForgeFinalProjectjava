package com.lifeforge;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.dao.AuditLogDao;
import com.lifeforge.dao.PasswordResetDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.model.Role;
import com.lifeforge.model.User;
import com.lifeforge.service.AuditLogService;
import com.lifeforge.service.CodeDeliveryService;
import com.lifeforge.service.PasswordResetService;
import com.lifeforge.service.PasswordResetService.RequestResult;
import com.lifeforge.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end password-reset flow against a live PostgreSQL database.
 * All tests are skipped (not failed) when the database is unreachable so
 * `./gradlew test` keeps passing on machines without a running PostgreSQL.
 *
 * <p>Dev code logging is enabled for this test suite so the plaintext code can
 * be read back from the service (mimicking what a real user receives in email).
 *
 * <p>The suite intentionally NEVER leaves test data behind: every test user it
 * creates is deleted again by {@code @AfterEach} (so cleanup also runs when a
 * test fails). An {@code @AfterAll} safety net and a suite-start purge both
 * remove any "Reset Tester" users leaked by earlier/interrupted runs. The purge
 * only matches this suite's own exact account format (full name "Reset Tester",
 * email {@code reset_*@test.local}, role USER) and can never touch real users.
 */
class PasswordResetServiceTest {

    private static final String TEST_FULL_NAME = "Reset Tester";
    private static final String TEST_EMAIL_DOMAIN = "@test.local";

    private static PasswordResetService service;
    private static PasswordResetDao resetDao;
    private static UserDao userDao;
    private static User testUser;

    /** Every test user created by this run, so none can be forgotten. */
    private static final List<User> createdUsers = new ArrayList<>();

    /** Test-user ids already removed, so cleanup never double-deletes. */
    private static final Set<Long> cleanedIds = new HashSet<>();

    @BeforeAll
    static void setUp() throws Exception {
        boolean dbUp = DatabaseConfig.testConnection();
        Assumptions.assumeTrue(dbUp, "PostgreSQL not reachable; skipping reset tests.");

        System.setProperty("lifeforge.dev.code.log", "true");

        AuditLogDao auditLogDao = new AuditLogDao();
        AuditLogService auditLogService = new AuditLogService(auditLogDao);
        resetDao = new PasswordResetDao();
        userDao = new UserDao();
        CodeDeliveryService delivery = (destination, code) -> { };
        service = new PasswordResetService(resetDao, userDao, auditLogService, delivery);

        purgeLeftoverTestUsers();
    }

    @BeforeEach
    void createUser() throws SQLException {
        String email = "reset_" + UUID.randomUUID().toString().substring(0, 8) + TEST_EMAIL_DOMAIN;
        User user = new User();
        user.setFullName(TEST_FULL_NAME);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtil.hash("OldPassw0rd"));
        user.setAge(25);
        user.setGender(Gender.MALE);
        user.setHeightCm(175.0);
        user.setWeightKg(70.0);
        user.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        user.setRole(Role.USER);
        user.setBlocked(false);
        testUser = userDao.create(user);
        createdUsers.add(testUser);
    }

    @AfterEach
    void deleteUser() throws SQLException {
        if (testUser != null) {
            cleanupUser(testUser.getId());
            testUser = null;
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        for (User created : new ArrayList<>(createdUsers)) {
            cleanupUser(created.getId());
        }
        createdUsers.clear();
        System.clearProperty("lifeforge.dev.code.log");
    }

    /**
     * Removes any "Reset Tester" accounts leaked by earlier or interrupted runs
     * of this suite, so a crash never permanently pollutes the application DB.
     * The match is this suite's exact account format only, never real users.
     */
    private static void purgeLeftoverTestUsers() throws SQLException {
        for (User u : userDao.findAll()) {
            if (isResetTesterAccount(u)) {
                cleanupUser(u.getId());
            }
        }
    }

    private static boolean isResetTesterAccount(User u) {
        String email = u.getEmail();
        return u.getRole() == Role.USER
                && TEST_FULL_NAME.equals(u.getFullName())
                && email != null
                && email.startsWith("reset_")
                && email.endsWith(TEST_EMAIL_DOMAIN);
    }

    private static void cleanupUser(Long userId) throws SQLException {
        if (userId == null || !cleanedIds.add(userId)) {
            return;
        }
        resetDao.invalidateForUser(userId);
        userDao.delete(userId);
    }

    /** Moves the user's latest request to APPROVED, as an administrator would. */
    private static void approveLatestRequest(Long userId) throws SQLException {
        var latest = resetDao.findLatestByUser(userId);
        assertTrue(latest.isPresent());
        resetDao.updateStatus(latest.get().getId(), PasswordResetService.STATUS_APPROVED);
    }

    @Test
    void unknownEmailGetsGenericMessageAndNoUserId() {
        RequestResult result =
                service.requestReset("nobody_" + UUID.randomUUID() + "@test.local");
        assertTrue(result.accepted);
        assertEquals(PasswordResetService.GENERIC_CONFIRMATION, result.message);
        assertNull(result.userId);
        assertNull(result.status);
    }

    @Test
    void invalidEmailIsRejected() {
        RequestResult result = service.requestReset("not-an-email");
        assertFalse(result.accepted);
    }

    @Test
    void fullFlowWrongThenRightCode() {
        Long userId = testUser.getId();

        RequestResult request = service.requestReset(testUser.getEmail());
        assertTrue(request.accepted);
        assertEquals(userId, request.userId);
        assertEquals(PasswordResetService.STATUS_PENDING, request.status);

        var wrong = service.verifyCode(userId, "000000");
        assertFalse(wrong.success);
        assertTrue(wrong.message.contains("Incorrect"));

        String code = service.devLastCode(userId);
        assertNotNull(code);
        var ok = service.verifyCode(userId, code);
        assertTrue(ok.success);
    }

    @Test
    void codeCannotBeReused() {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        String code = service.devLastCode(userId);
        assertTrue(service.verifyCode(userId, code).success);

        var second = service.verifyCode(userId, code);
        assertFalse(second.success);
    }

    @Test
    void resetThenLoginWithNewPassword() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        approveLatestRequest(userId);

        var reset = service.resetPassword(userId, "NewPass123", "NewPass123");
        assertTrue(reset.success);

        assertFalse(PasswordUtil.verify("OldPassw0rd",
                userDao.findById(userId).orElseThrow().getPasswordHash()));
        assertTrue(PasswordUtil.verify("NewPass123",
                userDao.findById(userId).orElseThrow().getPasswordHash()));
    }

    @Test
    void resetBlockedWhilePending() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());

        var reset = service.resetPassword(userId, "NewPass123", "NewPass123");
        assertFalse(reset.success);
        assertTrue(reset.message.contains("awaiting administrator approval"));

        assertTrue(PasswordUtil.verify("OldPassw0rd",
                userDao.findById(userId).orElseThrow().getPasswordHash()));
    }

    @Test
    void resetBlockedAfterRejection() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        var latest = resetDao.findLatestByUser(userId).orElseThrow();
        resetDao.updateStatus(latest.getId(), PasswordResetService.STATUS_REJECTED);

        var reset = service.resetPassword(userId, "NewPass123", "NewPass123");
        assertFalse(reset.success);
        assertTrue(reset.message.contains("rejected"));

        assertTrue(PasswordUtil.verify("OldPassw0rd",
                userDao.findById(userId).orElseThrow().getPasswordHash()));
    }

    @Test
    void completedRequestCannotBeReused() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        approveLatestRequest(userId);

        var first = service.resetPassword(userId, "NewPass123", "NewPass123");
        assertTrue(first.success);

        var second = service.resetPassword(userId, "Another987", "Another987");
        assertFalse(second.success);
        assertTrue(second.message.contains("already been completed"));

        assertTrue(PasswordUtil.verify("NewPass123",
                userDao.findById(userId).orElseThrow().getPasswordHash()));
    }

    @Test
    void repeatedRequestWhilePendingReturnsStatusWithoutNewCode() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        String code = service.devLastCode(userId);

        RequestResult again = service.requestReset(testUser.getEmail());
        assertTrue(again.accepted);
        assertEquals(userId, again.userId);
        assertEquals(PasswordResetService.STATUS_PENDING, again.status);

        assertTrue(service.verifyCode(userId, code).success,
                "A pending request must not be replaced by a duplicate request");
    }

    @Test
    void repeatedRequestAfterApprovalReturnsApprovedStatus() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        approveLatestRequest(userId);

        RequestResult again = service.requestReset(testUser.getEmail());
        assertTrue(again.accepted);
        assertEquals(PasswordResetService.STATUS_APPROVED, again.status);
    }

    @Test
    void mismatchedConfirmationRejectedWithoutChangingPassword() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        approveLatestRequest(userId);

        var reset = service.resetPassword(userId, "NewPass123", "Different123");
        assertFalse(reset.success);

        assertTrue(PasswordUtil.verify("OldPassw0rd",
                userDao.findById(userId).orElseThrow().getPasswordHash()));
    }

    @Test
    void tooManyWrongAttemptsBlocksTheCode() {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        String code = service.devLastCode(userId);

        boolean sawBlocked = false;
        for (int i = 0; i < 6; i++) {
            var result = service.verifyCode(userId, wrongCode(code));
            if (!result.success && result.message.contains("Too many incorrect attempts")) {
                sawBlocked = true;
                break;
            }
        }
        assertTrue(sawBlocked, "Expected the code to be blocked after repeated failed attempts");
        assertFalse(service.verifyCode(userId, code).success);
    }

    @Test
    void newCodeInvalidatesPreviousCode() {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        String firstCode = service.devLastCode(userId);

        service.resendCode(userId);
        String secondCode = service.devLastCode(userId);
        assertNotNull(secondCode);

        assertFalse(service.verifyCode(userId, firstCode).success,
                "Old code should be invalidated by a new request");
        assertTrue(service.verifyCode(userId, secondCode).success);
    }

    private String wrongCode(String code) {
        char first = code.charAt(0) == '0' ? '1' : '0';
        return first + code.substring(1);
    }
}