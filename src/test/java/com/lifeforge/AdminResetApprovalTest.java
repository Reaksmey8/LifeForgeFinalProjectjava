package com.lifeforge;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.dao.AuditLogDao;
import com.lifeforge.dao.PasswordResetDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.model.Role;
import com.lifeforge.model.User;
import com.lifeforge.service.AdminService;
import com.lifeforge.service.AuditLogService;
import com.lifeforge.service.CodeDeliveryService;
import com.lifeforge.service.PasswordResetService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the admin-side approval state machine for password reset requests
 * against a live PostgreSQL database. Skipped when the database is unreachable.
 *
 * <p>Every account it creates (one administrator + one ordinary user) is
 * deleted again in clean-up, matching the same hygiene rules as
 * {@link PasswordResetServiceTest}.
 */
class AdminResetApprovalTest {

    private static final String ADMIN_FULL_NAME = "Reset Admin Tester";
    private static final String TARGET_FULL_NAME = "Reset Target Tester";
    private static final String TEST_EMAIL_DOMAIN = "@test.local";

    private static AdminService adminService;
    private static PasswordResetService resetService;
    private static PasswordResetDao resetDao;
    private static UserDao userDao;
    private static User admin;
    private static User target;

    private static final List<Long> createdUserIds = new ArrayList<>();
    private static final Set<Long> cleanedIds = new HashSet<>();

    @BeforeAll
    static void setUp() throws Exception {
        boolean dbUp = DatabaseConfig.testConnection();
        Assumptions.assumeTrue(dbUp, "PostgreSQL not reachable; skipping admin approval tests.");

        userDao = new UserDao();
        resetDao = new PasswordResetDao();
        AuditLogService auditLogService = new AuditLogService(new AuditLogDao());
        adminService = new AdminService(userDao, auditLogService, resetDao);
        CodeDeliveryService delivery = (destination, code) -> { };
        resetService = new PasswordResetService(resetDao, userDao, auditLogService, delivery);

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
    }

    private static User newUser(String fullName, String email, Role role) {
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setUsername(null);
        user.setPasswordHash(PasswordUtil.hash("OldPassw0rd"));
        user.setAge(30);
        user.setGender(Gender.MALE);
        user.setHeightCm(175.0);
        user.setWeightKg(70.0);
        user.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        user.setRole(role);
        user.setBlocked(false);
        return user;
    }

    private static void purgeLeftoverTestUsers() throws SQLException {
        for (User u : userDao.findAll()) {
            if (isTesterAccount(u)) {
                cleanupUser(u.getId());
            }
        }
    }

    private static boolean isTesterAccount(User u) {
        String email = u.getEmail();
        boolean isThisSuite = TARGET_FULL_NAME.equals(u.getFullName())
                || ADMIN_FULL_NAME.equals(u.getFullName());
        return (u.getRole() == Role.USER || u.getRole() == Role.ADMIN)
                && isThisSuite
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

    private Long latestRequestId(Long userId) throws SQLException {
        return resetDao.findLatestByUser(userId).orElseThrow().getId();
    }

    private void requestResetFor(Long userId) throws SQLException {
        String email = userDao.findById(userId).orElseThrow().getEmail();
        var result = resetService.requestReset(email);
        assertTrue(result.accepted);
    }

    @Test
    void nonAdminCannotApprove() {
        AdminService.AdminActionResult result =
                adminService.approveResetRequest(target, 1L);
        assertFalse(result.success);
        assertTrue(result.message.contains("admin role required"));
    }

    @Test
    void nonAdminCannotReject() {
        AdminService.AdminActionResult result =
                adminService.rejectResetRequest(target, 1L);
        assertFalse(result.success);
        assertTrue(result.message.contains("admin role required"));
    }

    @Test
    void adminCannotDecideOnOwnRequest() throws SQLException {
        requestResetFor(admin.getId());
        Long id = latestRequestId(admin.getId());

        AdminService.AdminActionResult approve =
                adminService.approveResetRequest(admin, id);
        assertFalse(approve.success);
        assertTrue(approve.message.contains("own"));

        AdminService.AdminActionResult reject =
                adminService.rejectResetRequest(admin, id);
        assertFalse(reject.success);
        assertTrue(reject.message.contains("own"));
    }

    @Test
    void approveMarksRequestApproved() throws SQLException {
        requestResetFor(target.getId());
        Long id = latestRequestId(target.getId());

        AdminService.AdminActionResult result =
                adminService.approveResetRequest(admin, id);
        assertTrue(result.success, result.message);
        assertEquals(PasswordResetService.STATUS_APPROVED,
                resetDao.findById(id).orElseThrow().getStatus());
    }

    @Test
    void rejectMarksRequestRejected() throws SQLException {
        requestResetFor(target.getId());
        Long id = latestRequestId(target.getId());

        AdminService.AdminActionResult result =
                adminService.rejectResetRequest(admin, id);
        assertTrue(result.success, result.message);
        assertEquals(PasswordResetService.STATUS_REJECTED,
                resetDao.findById(id).orElseThrow().getStatus());
    }

    @Test
    void onlyPendingRequestCanBeDecided() throws SQLException {
        requestResetFor(target.getId());
        Long id = latestRequestId(target.getId());

        assertTrue(adminService.approveResetRequest(admin, id).success);

        AdminService.AdminActionResult secondApprove =
                adminService.approveResetRequest(admin, id);
        assertFalse(secondApprove.success);
        assertTrue(secondApprove.message.contains("Only pending"));

        AdminService.AdminActionResult rejectAfterApprove =
                adminService.rejectResetRequest(admin, id);
        assertFalse(rejectAfterApprove.success);
        assertTrue(rejectAfterApprove.message.contains("Only pending"));
    }

    @Test
    void missingRequestIdIsRejected() {
        AdminService.AdminActionResult result =
                adminService.approveResetRequest(admin, null);
        assertFalse(result.success);
    }

    @Test
    void approvedRequestAllowsPasswordReset() throws SQLException {
        requestResetFor(target.getId());
        Long id = latestRequestId(target.getId());
        assertTrue(adminService.approveResetRequest(admin, id).success);

        var reset = resetService.resetPassword(
                target.getId(), "BrandNew456", "BrandNew456");
        assertTrue(reset.success);
        assertTrue(PasswordUtil.verify("BrandNew456",
                userDao.findById(target.getId()).orElseThrow().getPasswordHash()));
        assertEquals(PasswordResetService.STATUS_COMPLETED,
                resetDao.findById(id).orElseThrow().getStatus());
    }
}