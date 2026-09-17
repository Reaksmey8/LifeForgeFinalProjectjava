package com.lifeforge.controller;

import com.lifeforge.Session;
import com.lifeforge.dao.PasswordResetDao;
import com.lifeforge.model.AuditLog;
import com.lifeforge.model.PasswordReset;
import com.lifeforge.model.Role;
import com.lifeforge.model.User;
import com.lifeforge.service.AdminService;
import com.lifeforge.service.AnalyticsService;
import com.lifeforge.service.AuditLogService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Admin-only operations: user management, analytics and audit logs.
 * Every operation re-verifies that the session user is an ADMIN
 * before delegating to AdminService (which performs its own guard).
 */
public class AdminController extends BaseController {

    private final AdminService adminService;
    private final AnalyticsService analyticsService;
    private final AuditLogService auditLogService;
    private final Session session;

    public AdminController(AdminService adminService, AnalyticsService analyticsService,
                           AuditLogService auditLogService, Session session) {
        this.adminService = adminService;
        this.analyticsService = analyticsService;
        this.auditLogService = auditLogService;
        this.session = session;
    }

    public AdminController(AdminService adminService, AnalyticsService analyticsService,
                           AuditLogService auditLogService, PasswordResetDao passwordResetDao,
                           Session session) {
        this(adminService, analyticsService, auditLogService, session);
    }

    private User actor() {
        User actor = session.getCurrentUser();
        if (actor == null) {
            setError("Not logged in.");
            return null;
        }
        return actor;
    }

    private boolean notAdmin(User actor) {
        if (actor == null) {
            return true;
        }
        if (actor.getRole() != Role.ADMIN) {
            setError("Admin role required.");
            return true;
        }
        return false;
    }

    public List<User> listAllUsers() {
        User actor = actor();
        if (notAdmin(actor)) {
            return null;
        }
        try {
            return adminService.listAllUsers(actor);
        } catch (SQLException e) {
            setError(e, "Failed to load users.");
            return null;
        }
    }

    public List<User> searchUsers(String term) {
        User actor = actor();
        if (notAdmin(actor)) {
            return null;
        }
        try {
            return adminService.searchUsers(actor, term);
        } catch (SQLException e) {
            setError(e, "Failed to search users.");
            return null;
        }
    }

    public boolean blockUser(Long targetUserId) {
        User actor = actor();

        if (notAdmin(actor)) {
            return false;
        }

        AdminService.AdminActionResult result =
                adminService.blockUser(actor, targetUserId);

        return applyResult(result);
    }

    public boolean unblockUser(Long targetUserId) {
        User actor = actor();
        if (notAdmin(actor)) {
            return false;
        }
        AdminService.AdminActionResult result = adminService.unblockUser(actor, targetUserId);
        return applyResult(result);
    }

//    public boolean changeRole(Long targetUserId, Role newRole) {
//        User actor = actor();
//        if (notAdmin(actor)) {
//            return false;
//        }
//        AdminService.AdminActionResult result = adminService.changeUserRole(actor, targetUserId, newRole);
//        return applyResult(result);
//    }

    public boolean deleteUser(Long targetUserId) {
        User actor = actor();
        if (notAdmin(actor)) {
            return false;
        }
        AdminService.AdminActionResult result = adminService.deleteUser(actor, targetUserId);
        return applyResult(result);
    }

    public Optional<User> findUserById(Long userId) {
        try {
            return adminService.findUserById(userId);
        } catch (SQLException e) {
            setError(e, "Failed to load this user.");
            return Optional.empty();
        }
    }

    public AnalyticsService.AnalyticsSummary buildAnalytics() {
        User actor = actor();
        if (notAdmin(actor)) {
            return null;
        }
        try {
            return analyticsService.buildSummary();
        } catch (SQLException e) {
            setError(e, "Failed to build analytics.");
            return null;
        }
    }

    public List<AuditLog> getRecentLogs(int limit) {
        User actor = actor();
        if (notAdmin(actor)) {
            return null;
        }
        try {
            return auditLogService.getRecentLogs(limit);
        } catch (SQLException e) {
            setError(e, "Failed to load audit logs.");
            return null;
        }
    }

    public List<AuditLog> getAllLogs() {
        User actor = actor();
        if (notAdmin(actor)) {
            return null;
        }
        try {
            return auditLogService.getAllLogs();
        } catch (SQLException e) {
            setError(e, "Failed to load audit logs.");
            return null;
        }
    }

    private boolean applyResult(AdminService.AdminActionResult result) {
        if (!result.success) {
            setError(result.message);
            return false;
        }
        return true;
    }
}