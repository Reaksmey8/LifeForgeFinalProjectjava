package com.lifeforge.service;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.dao.AuditLogDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.model.Role;
import com.lifeforge.model.User;
import com.lifeforge.service.AuditLogService;
import com.lifeforge.service.CodeDeliveryService;
import com.lifeforge.service.PasswordResetService;
import com.lifeforge.service.PasswordResetService.RequestResult;
import com.lifeforge.service.PasswordResetService.VerifyResult;
import com.lifeforge.service.PasswordResetService.ResetResult;
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
 * Tests the self-service verification-code password reset flow against a live PostgreSQL database.
 * No administrator manual approval is required.
 */
class PasswordResetServiceTest {

    private static final String TEST_FULL_NAME = "Reset Tester";
    private static final String TEST_EMAIL_DOMAIN = "@test.local";

    private static PasswordResetService service;
    private static UserDao userDao;
    private static User testUser;

    private static final List<User> createdUsers = new ArrayList<>();
    private static final Set<Long> cleanedIds = new HashSet<>();

    @BeforeAll
    static void setUp() throws Exception {
        boolean dbUp = DatabaseConfig.testConnection();
        Assumptions.assumeTrue(dbUp, "PostgreSQL not reachable; skipping reset tests.");

        System.setProperty("lifeforge.dev.code.log", "true");

        AuditLogDao auditLogDao = new AuditLogDao();
        AuditLogService auditLogService = new AuditLogService(auditLogDao);
        userDao = new UserDao();
        CodeDeliveryService delivery = (destination, code) -> { };
        service = new PasswordResetService(userDao, auditLogService, delivery);

        purgeLeftoverTestUsers();
    }

    @BeforeEach
    void createUser() throws SQLException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "reset_" + suffix + TEST_EMAIL_DOMAIN;
        String username = "resetuser_" + suffix;
        User user = new User();
        user.setFullName(TEST_FULL_NAME);
        user.setUsername(username);
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
        userDao.delete(userId);
    }

    @Test
    void requestResetByEmailSuccess() {
        RequestResult result = service.requestReset(testUser.getEmail());
        assertTrue(result.accepted);
        assertEquals(testUser.getId(), result.userId);
        assertNotNull(service.devLastCode(testUser.getId()));
    }

    @Test
    void requestResetByUsernameSuccess() {
        RequestResult result = service.requestReset(testUser.getUsername());
        assertTrue(result.accepted);
        assertEquals(testUser.getId(), result.userId);
        assertNotNull(service.devLastCode(testUser.getId()));
    }

    @Test
    void unknownAccountIsRejected() {
        RequestResult result = service.requestReset("nonexistent_user_" + UUID.randomUUID());
        assertFalse(result.accepted);
        assertTrue(result.message.contains("No account found"));
    }

    @Test
    void emptyOrNullInputIsRejected() {
        RequestResult empty = service.requestReset("");
        assertFalse(empty.accepted);
        RequestResult nullRes = service.requestReset(null);
        assertFalse(nullRes.accepted);
    }

    @Test
    void blockedAccountIsRejected() throws SQLException {
        userDao.setBlocked(testUser.getId(), true);

        RequestResult result = service.requestReset(testUser.getEmail());
        assertFalse(result.accepted);
        assertTrue(result.message.contains("blocked"));
    }

    @Test
    void wrongVerificationCodeIsRejected() {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());

        VerifyResult wrong = service.verifyCode(userId, "000000");
        assertFalse(wrong.success);
        assertTrue(wrong.message.contains("Incorrect"));

        VerifyResult invalidLen = service.verifyCode(userId, "123");
        assertFalse(invalidLen.success);
        assertTrue(invalidLen.message.contains("6 digits"));
    }

    @Test
    void correctVerificationCodePasses() {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        String code = service.devLastCode(userId);
        assertNotNull(code);

        VerifyResult ok = service.verifyCode(userId, code);
        assertTrue(ok.success);
    }

    @Test
    void resendCodeGeneratesNewCodeAndInvalidatesOld() {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        String firstCode = service.devLastCode(userId);

        RequestResult resend = service.resendCode(userId);
        assertTrue(resend.accepted);
        String secondCode = service.devLastCode(userId);
        assertNotNull(secondCode);

        assertFalse(service.verifyCode(userId, firstCode).success,
                "Old code must be invalidated by new request");
        assertTrue(service.verifyCode(userId, secondCode).success,
                "New code must be accepted");
    }

    @Test
    void directSelfServicePasswordResetSuccess() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        String code = service.devLastCode(userId);

        // Self-service reset: NO ADMIN APPROVAL REQUIRED
        ResetResult reset = service.resetPassword(userId, code, "BrandNewPass123", "BrandNewPass123");
        assertTrue(reset.success);

        // Verify password changed in PostgreSQL
        User updated = userDao.findById(userId).orElseThrow();
        assertFalse(PasswordUtil.verify("OldPassw0rd", updated.getPasswordHash()));
        assertTrue(PasswordUtil.verify("BrandNewPass123", updated.getPasswordHash()));

        // Token consumed: cannot be reused
        ResetResult reused = service.resetPassword(userId, code, "AnotherPass123", "AnotherPass123");
        assertFalse(reused.success);
    }

    @Test
    void passwordMismatchRejectedWithoutChangingPassword() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        String code = service.devLastCode(userId);

        ResetResult reset = service.resetPassword(userId, code, "BrandNewPass123", "MismatchPass999");
        assertFalse(reset.success);
        assertTrue(reset.message.contains("match"));

        User unchanged = userDao.findById(userId).orElseThrow();
        assertTrue(PasswordUtil.verify("OldPassw0rd", unchanged.getPasswordHash()));
    }

    @Test
    void shortPasswordRejectedWithoutChangingPassword() throws Exception {
        Long userId = testUser.getId();
        service.requestReset(testUser.getEmail());
        String code = service.devLastCode(userId);

        ResetResult reset = service.resetPassword(userId, code, "short", "short");
        assertFalse(reset.success);
        assertTrue(reset.message.contains("characters"));

        User unchanged = userDao.findById(userId).orElseThrow();
        assertTrue(PasswordUtil.verify("OldPassw0rd", unchanged.getPasswordHash()));
    }
}