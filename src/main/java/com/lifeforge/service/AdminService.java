package com.lifeforge.service;

import com.lifeforge.dao.PasswordResetDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.model.PasswordReset;
import com.lifeforge.model.Role;
import com.lifeforge.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Admin-only user-management operations. Every method requires the
 * caller to pass the acting admin's User so we can (a) verify role
 * server-side, never trusting the TUI menu alone, and (b) attribute
 * audit log entries correctly.
 */
public class AdminService {

    private final UserDao userDao;
    private final AuditLogService auditLogService;
    private final PasswordResetDao resetDao;

    public AdminService(UserDao userDao, AuditLogService auditLogService, PasswordResetDao resetDao) {
        this.userDao = userDao;
        this.auditLogService = auditLogService;
        this.resetDao = resetDao;
    }

    public static class AdminActionResult {
        public final boolean success;
        public final String message;

        private AdminActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static AdminActionResult ok(String message) {
            return new AdminActionResult(true, message);
        }

        public static AdminActionResult fail(String message) {
            return new AdminActionResult(false, message);
        }
    }

    private AdminActionResult requireAdmin(User actor) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return AdminActionResult.fail("Unauthorized: admin role required.");
        }
        return null;
    }

    public List<User> listAllUsers(User actor) throws SQLException {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            throw new SecurityException("Unauthorized: admin role required.");
        }

        return userDao.findAll()
                .stream()
                .filter(user -> user.getRole() == Role.USER)
                .toList();
    }

    public List<User> searchUsers(User actor, String term) throws SQLException {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            throw new SecurityException("Unauthorized: admin role required.");
        }

        return userDao.searchByNameOrEmail(term)
                .stream()
                .filter(user -> user.getRole() == Role.USER)
                .toList();
    }

    public AdminActionResult blockUser(User actor, Long targetUserId) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return new AdminActionResult(false, "Admin access required.");
        }

        if (targetUserId == null) {
            return new AdminActionResult(false, "User ID is required.");
        }

        if (actor.getId().equals(targetUserId)) {
            return new AdminActionResult(false, "Admins cannot block their own account.");
        }

        try {
            Optional<User> targetOpt = userDao.findById(targetUserId);

            if (targetOpt.isEmpty()) {
                return new AdminActionResult(false, "User not found.");
            }

            User target = targetOpt.get();

            if (target.getRole() == Role.ADMIN) {
                return new AdminActionResult(false,
                        "Admins cannot block another admin account.");
            }

            userDao.setBlocked(targetUserId, true);

            auditLogService.log(
                    actor.getId(),
                    "BLOCK_USER",
                    "USER",
                    targetUserId,
                    "Blocked user account"
            );

            return new AdminActionResult(true, "User blocked successfully.");

        } catch (SQLException e) {
            return new AdminActionResult(false,
                    "Failed to block user: " + e.getMessage());
        }
    }

    public AdminActionResult unblockUser(User actor, Long targetUserId) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            return new AdminActionResult(false, "Admin access required.");
        }

        if (targetUserId == null) {
            return new AdminActionResult(false, "User ID is required.");
        }

        if (actor.getId().equals(targetUserId)) {
            return new AdminActionResult(false,
                    "Admins cannot modify their own account here.");
        }

        try {
            Optional<User> targetOpt = userDao.findById(targetUserId);

            if (targetOpt.isEmpty()) {
                return new AdminActionResult(false, "User not found.");
            }

            User target = targetOpt.get();

            if (target.getRole() == Role.ADMIN) {
                return new AdminActionResult(false,
                        "Admins cannot modify another admin account.");
            }

            userDao.setBlocked(targetUserId, false);

            auditLogService.log(
                    actor.getId(),
                    "UNBLOCK_USER",
                    "USER",
                    targetUserId,
                    "Unblocked user account"
            );

            return new AdminActionResult(true, "User unblocked successfully.");

        } catch (SQLException e) {
            return new AdminActionResult(false,
                    "Failed to unblock user: " + e.getMessage());
        }
    }

//    public AdminActionResult changeUserRole(User actor, Long targetUserId, Role newRole) {
//        AdminActionResult authError = requireAdmin(actor);
//        if (authError != null) {
//            return authError;
//        }
//        try {
//            userDao.updateRole(targetUserId, newRole);
//            auditLogService.log(actor.getId(), "ROLE_CHANGED", "USER", targetUserId,
//                    "Role changed to " + newRole + " by admin " + actor.getEmail());
//            return AdminActionResult.ok("User role updated successfully.");
//        } catch (SQLException e) {
//            return AdminActionResult.fail("Failed to update role due to a database error.");
//        }
//    }


    public AdminActionResult deleteUser(User actor, Long targetUserId) {
        AdminActionResult authError = requireAdmin(actor);
        if (authError != null) {
            return authError;
        }
        if (actor.getId().equals(targetUserId)) {
            return AdminActionResult.fail("Admins cannot delete their own account from this screen.");
        }
        try {
            userDao.delete(targetUserId);
            auditLogService.log(actor.getId(), "USER_DELETED", "USER", targetUserId,
                    "Deleted by admin " + actor.getEmail());
            return AdminActionResult.ok("User deleted successfully.");
        } catch (SQLException e) {
            return AdminActionResult.fail("Failed to delete user due to a database error.");
        }
    }

    public Optional<User> findUserById(Long userId) throws SQLException {
        return userDao.findById(userId);
    }

    public AdminActionResult approveResetRequest(User actor, Long requestId) {
        AdminActionResult authError = requireAdmin(actor);
        if (authError != null) {
            return authError;
        }
        return applyResetDecision(actor, requestId, true);
    }

    public AdminActionResult rejectResetRequest(User actor, Long requestId) {
        AdminActionResult authError = requireAdmin(actor);
        if (authError != null) {
            return authError;
        }
        return applyResetDecision(actor, requestId, false);
    }

    /**
     * Shared approve/reject logic. A request must still be PENDING to be
     * decided on, and an admin can never decide on their own request.
     */
    private AdminActionResult applyResetDecision(User actor, Long requestId, boolean approve) {
        if (requestId == null) {
            return AdminActionResult.fail("A reset request ID is required.");
        }
        String verb = approve ? "approved" : "rejected";
        String cannotVerb = approve ? "approve" : "reject";
        try {
            Optional<PasswordReset> requestOpt = resetDao.findById(requestId);
            if (requestOpt.isEmpty()) {
                return AdminActionResult.fail("Reset request #" + requestId + " was not found.");
            }
            PasswordReset request = requestOpt.get();
            if (request.getUserId().equals(actor.getId())) {
                return AdminActionResult.fail(
                        "Admins cannot " + cannotVerb + " their own password reset request.");
            }
            if (!PasswordResetService.STATUS_PENDING.equals(request.getStatus())) {
                return AdminActionResult.fail(
                        "Only pending requests can be " + verb + ".");
            }
            resetDao.updateStatus(requestId, approve
                    ? PasswordResetService.STATUS_APPROVED
                    : PasswordResetService.STATUS_REJECTED);
            auditLogService.log(actor.getId(),
                    approve ? "PASSWORD_RESET_REQUEST_APPROVED" : "PASSWORD_RESET_REQUEST_REJECTED",
                    "PASSWORD_RESET", requestId,
                    "Request #" + requestId + " " + verb + " by admin " + actor.getEmail());
            return AdminActionResult.ok(approve
                    ? "Password reset request #" + requestId + " approved. "
                            + "The user can now create a new password."
                    : "Password reset request #" + requestId + " rejected. "
                            + "The user cannot reset their password.");
        } catch (SQLException e) {
            return AdminActionResult.fail(
                    "Failed to update the request due to a database error.");
        }
    }
}