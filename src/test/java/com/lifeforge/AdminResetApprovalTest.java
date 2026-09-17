package com.lifeforge;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.dao.AuditLogDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.model.Role;
import com.lifeforge.model.User;
import com.lifeforge.service.AdminService;
import com.lifeforge.service.AuditLogService;
import com.lifeforge.service.CodeDeliveryService;
import com.lifeforge.service.PasswordResetService;
import com.lifeforge.service.PasswordResetService.RequestResult;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that password resets no longer require administrative manual approval,
 * and that administrative account status (such as block/unblock) properly controls
 * reset eligibility.
 */
class AdminResetApprovalTest {

    private static final String ADMIN_FULL_NAME = "Reset Admin Tester";
    private static final String TARGET_FULL_NAME = "Reset Target Tester";
    private static final String TEST_EMAIL_DOMAIN = "@test.local";

    private static AdminService adminService;
    private static PasswordResetService resetService;
    private static UserDao userDao;
    private static User admin;
    private static User target;

    private static final List<Long> createdUserIds = new ArrayList<>();
    private static final Set<Long> cleanedIds = new HashSet<>();

    @BeforeAll
    static void setUp() throws Exception {
        boolean dbUp = DatabaseConfig.testConnection();
        Assumptions.assumeTrue(dbUp, "PostgreSQL not reachable; skipping admin approval tests.");

        System.setProperty("lifeforge.dev.code.log", "true");

        userDao = new UserDao();
        AuditLogService auditLogService = new AuditLogService(new AuditLogDao());
        adminService = new AdminService(userDao, auditLogService);
        CodeDeliveryService delivery = (destination, code) -> { };
        resetService = new PasswordResetService(userDao, auditLogService, delivery);

        purgeLeftoverTestUsers();
    }

    @BeforeEach
    void createAccounts() throws SQLException {
        String email = "reset_target_" + UUID.randomUUID().toString().substring(0, 8) + TEST_EMAIL_DOMAIN;
        target = newUser(TARGET_FULL_NAME, email, Role.USER);
        target = userDao.create(target);
        createdUserIds.add(target.getId());

        String adminEmail = "reset_admin_" + UUID.randomUUID().toString().substring(0, 8) + TEST_EMAIL_DOMAIN;
        admin = newUser(ADMIN_FULL_NAME, adminEmail, Role.ADMIN);
        admin = userDao.create(admin);
        createdUserIds.add(admin.getId());
    }

    @AfterEach
    void deleteAccounts() throws SQLException {
        if (target != null) {
            cleanupUser(target.getId());
            target = null;
        }
        if (admin != null) {
            cleanupUser(admin.getId());
            admin = null;
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        for (Long id : new ArrayList<>(createdUserIds)) {
            cleanupUser(id);
        }
        createdUserIds.clear();
        System.clearProperty("lifeforge.dev.code.log");
    }

    private static void purgeLeftoverTestUsers() throws SQLException {
        for (User u : userDao.findAll()) {
            if (isTestAccount(u)) {
                cleanupUser(u.getId());
            }
        }
    }

    private static boolean isTestAccount(User u) {
        String email = u.getEmail();
        return (ADMIN_FULL_NAME.equals(u.getFullName()) || TARGET_FULL_NAME.equals(u.getFullName()))
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

    private static User newUser(String fullName, String email, Role role) {
        User u = new User();
        u.setFullName(fullName);
        u.setEmail(email);
        u.setPasswordHash(PasswordUtil.hash("InitialPass123"));
        u.setAge(30);
        u.setGender(Gender.FEMALE);
        u.setHeightCm(165.0);
        u.setWeightKg(58.0);
        u.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        u.setRole(role);
        u.setBlocked(false);
        return u;
    }

    @Test
    void userResetsPasswordDirectlyWithoutAdminIntervention() throws Exception {
        // User initiates reset
        RequestResult req = resetService.requestReset(target.getEmail());
        assertTrue(req.accepted, "Reset request should be accepted without admin queuing");

        String code = resetService.devLastCode(target.getId());

        // User directly verifies and resets without admin approval
        ResetResult reset = resetService.resetPassword(target.getId(), code, "DirectNewPass123", "DirectNewPass123");
        assertTrue(reset.success, "Password reset must succeed directly without requiring admin approval");

        User updated = userDao.findById(target.getId()).orElseThrow();
        assertTrue(PasswordUtil.verify("DirectNewPass123", updated.getPasswordHash()));
    }

    @Test
    void adminBlockingUserPreventsPasswordReset() throws Exception {
        // Admin blocks user account
        AdminService.AdminActionResult blockResult = adminService.blockUser(admin, target.getId());
        assertTrue(blockResult.success, "Admin should be able to block user");

        // User tries to request reset -> rejected because account is blocked
        RequestResult req = resetService.requestReset(target.getEmail());
        assertFalse(req.accepted, "Blocked user must not be allowed to request password reset");
        assertTrue(req.message.contains("blocked"));
    }

    @Test
    void adminUnblockingUserRestoresPasswordResetEligibility() throws Exception {
        // First block
        adminService.blockUser(admin, target.getId());

        // Then unblock
        AdminService.AdminActionResult unblockResult = adminService.unblockUser(admin, target.getId());
        assertTrue(unblockResult.success, "Admin should be able to unblock user");

        // User can now request reset
        RequestResult req = resetService.requestReset(target.getEmail());
        assertTrue(req.accepted, "Unblocked user should now be eligible to reset password");
    }
}