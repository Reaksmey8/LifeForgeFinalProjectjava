package com.lifeforge.service;

import com.lifeforge.dao.UserDao;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.model.User;
import com.lifeforge.util.PasswordUtil;
import com.lifeforge.util.ValidationUtil;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Business logic for user profile management (READ/UPDATE/DELETE -
 * CREATE is handled by AuthService.register during registration).
 * Profile updates are NOT daily tracking; they simply update the
 * baseline data used by future recommendation calculations.
 */
public class UserService {

    private final UserDao userDao;
    private final AuditLogService auditLogService;

    public UserService(UserDao userDao, AuditLogService auditLogService) {
        this.userDao = userDao;
        this.auditLogService = auditLogService;
    }

    public static class UpdateResult {
        public final boolean success;
        public final String errorMessage;

        private UpdateResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static UpdateResult ok() {
            return new UpdateResult(true, null);
        }

        public static UpdateResult fail(String message) {
            return new UpdateResult(false, message);
        }
    }

    public Optional<User> getProfile(Long userId) throws SQLException {
        return userDao.findById(userId);
    }

    public UpdateResult updateProfile(Long userId, String fullName, Integer age, Gender gender,
                                      Double heightCm, Double weightKg, ActivityLevel activityLevel) {
        String[] checks = {
                ValidationUtil.validateFullName(fullName),
                ValidationUtil.validateAge(age),
                ValidationUtil.validateHeight(heightCm),
                ValidationUtil.validateWeight(weightKg)
        };
        for (String check : checks) {
            if (check != null) {
                return UpdateResult.fail(check);
            }
        }

        try {
            Optional<User> existing = userDao.findById(userId);
            if (existing.isEmpty()) {
                return UpdateResult.fail("User not found.");
            }

            User user = existing.get();
            user.setFullName(fullName.trim());
            user.setAge(age);
            user.setGender(gender);
            user.setHeightCm(heightCm);
            user.setWeightKg(weightKg);
            user.setActivityLevel(activityLevel);

            userDao.updateProfile(user);
            return UpdateResult.ok();

        } catch (SQLException e) {
            return UpdateResult.fail("Failed to update profile due to a database error.");
        }
    }

    public UpdateResult changePassword(Long userId, String currentPassword, String newPassword,
                                       String confirmNewPassword) {
        String passwordCheck = ValidationUtil.validatePassword(newPassword);
        if (passwordCheck != null) {
            return UpdateResult.fail(passwordCheck);
        }
        String confirmCheck = ValidationUtil.validatePasswordConfirmation(newPassword, confirmNewPassword);
        if (confirmCheck != null) {
            return UpdateResult.fail(confirmCheck);
        }

        try {
            Optional<User> existing = userDao.findById(userId);
            if (existing.isEmpty()) {
                return UpdateResult.fail("User not found.");
            }
            if (!PasswordUtil.verify(currentPassword, existing.get().getPasswordHash())) {
                return UpdateResult.fail("Current password is incorrect.");
            }

            userDao.updatePassword(userId, PasswordUtil.hash(newPassword));
            return UpdateResult.ok();

        } catch (SQLException e) {
            return UpdateResult.fail("Failed to change password due to a database error.");
        }
    }

    public UpdateResult deleteAccount(Long userId) {
        try {
            userDao.delete(userId);
            auditLogService.log(userId, "USER_DELETED", "USER", userId,
                    "User deleted their own account.");
            return UpdateResult.ok();
        } catch (SQLException e) {
            return UpdateResult.fail("Failed to delete account due to a database error.");
        }
    }
}