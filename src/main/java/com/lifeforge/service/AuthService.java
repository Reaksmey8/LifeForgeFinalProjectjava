package com.lifeforge.service;

import com.lifeforge.dao.AuditLogDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.model.*;
import com.lifeforge.util.PasswordUtil;
import com.lifeforge.util.ValidationUtil;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Handles registration, login, and authorization checks.
 * Contains no SQL and no TUI code - pure business logic.
 */
public class AuthService {

    private final UserDao userDao;
    private final AuditLogService auditLogService;

    public AuthService(UserDao userDao, AuditLogService auditLogService) {
        this.userDao = userDao;
        this.auditLogService = auditLogService;
    }

    /**
     * Result of a registration attempt. If successful, user is populated
     * and errorMessage is null; otherwise errorMessage explains why.
     */
    public static class RegistrationResult {
        public final boolean success;
        public final String errorMessage;
        public final User user;

        private RegistrationResult(boolean success, String errorMessage, User user) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.user = user;
        }

        public static RegistrationResult ok(User user) {
            return new RegistrationResult(true, null, user);
        }

        public static RegistrationResult fail(String message) {
            return new RegistrationResult(false, message, null);
        }
    }

    public static class LoginResult {
        public final boolean success;
        public final String errorMessage;
        public final User user;

        private LoginResult(boolean success, String errorMessage, User user) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.user = user;
        }

        public static LoginResult ok(User user) {
            return new LoginResult(true, null, user);
        }

        public static LoginResult fail(String message) {
            return new LoginResult(false, message, null);
        }
    }

    public RegistrationResult register(String username, String email, String password,
                                       String confirmPassword, Integer age, Gender gender,
                                       Double heightCm, Double weightKg, ActivityLevel activityLevel) {
        return register(username, username, email, password, confirmPassword, age, gender, heightCm, weightKg, activityLevel);
    }

    public RegistrationResult register(String username, String fullName, String email, String password,
                                       String confirmPassword, Integer age, Gender gender,
                                       Double heightCm, Double weightKg, ActivityLevel activityLevel) {
        String error = validateRegistration(username, email, password, confirmPassword, age, heightCm, weightKg);
        if (error != null) {
            return RegistrationResult.fail(error);
        }

        try {
            String trimmedUsername = username.trim();
            if (userDao.usernameExists(trimmedUsername)) {
                return RegistrationResult.fail("An account with this username already exists.");
            }

            String normalizedEmail = email.trim().toLowerCase();
            if (userDao.emailExists(normalizedEmail)) {
                return RegistrationResult.fail("An account with this email already exists.");
            }

            User user = new User();
            user.setUsername(trimmedUsername);
            String resolvedFullName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : trimmedUsername;
            user.setFullName(resolvedFullName);
            user.setEmail(normalizedEmail);
            user.setPasswordHash(PasswordUtil.hash(password));
            user.setAge(age);
            user.setGender(gender);
            user.setHeightCm(heightCm);
            user.setWeightKg(weightKg);
            user.setActivityLevel(activityLevel);
            user.setRole(Role.USER);
            user.setBlocked(false);

            User created = userDao.create(user);
            return RegistrationResult.ok(created);

        } catch (SQLException e) {
            return RegistrationResult.fail("Registration failed due to a database error. Please try again.");
        }
    }

    /** Derby-username derived from the email's local part, guaranteed unique in the users table. */
    private String uniqueUsername(String email) throws SQLException {
        String local = email;
        int at = email.indexOf('@');
        if (at > 0) {
            local = email.substring(0, at);
        }
        String base = local.replaceAll("[^a-zA-Z0-9._-]", "").toLowerCase();
        if (base.isEmpty()) {
            base = "user";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        if (!userDao.usernameExists(base)) {
            return base;
        }
        for (int i = 1; i < 1000; i++) {
            String candidate = base + i;
            if (candidate.length() > 100) {
                candidate = candidate.substring(0, 97) + i;
            }
            if (!userDao.usernameExists(candidate)) {
                return candidate;
            }
        }
        return base + System.nanoTime();
    }

    private String validateRegistration(String username, String email, String password,
                                        String confirmPassword, Integer age, Double heightCm, Double weightKg) {
        String[] checks = {
                ValidationUtil.validateUsername(username),
                ValidationUtil.validateEmail(email),
                ValidationUtil.validatePassword(password),
                ValidationUtil.validatePasswordConfirmation(password, confirmPassword),
                ValidationUtil.validateAge(age),
                ValidationUtil.validateHeight(heightCm),
                ValidationUtil.validateWeight(weightKg)
        };
        for (String check : checks) {
            if (check != null) {
                return check;
            }
        }
        return null;
    }

    public LoginResult login(String login, String password) {
        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            return LoginResult.fail("Email/username and password are required.");
        }

        try {
            Optional<User> found = userDao.findByEmailOrUsername(login.trim().toLowerCase());
            if (found.isEmpty()) {
                return LoginResult.fail("Invalid email/username or password.");
            }

            User user = found.get();

            if (!PasswordUtil.verify(password, user.getPasswordHash())) {
                return LoginResult.fail("Invalid email/username or password.");
            }

            if (user.isBlocked()) {
                return LoginResult.fail("This account has been blocked. Please contact an administrator.");
            }

            auditLogService.log(user.getId(), "LOGIN", "USER", user.getId(),
                    "User logged in: " + user.getEmail());

            return LoginResult.ok(user);

        } catch (SQLException e) {
            return LoginResult.fail("Login failed due to a database error. Please try again.");
        }
    }

    /**
     * Authorization guard used by controllers before any admin operation.
     * Never rely on the TUI menu alone to hide admin actions.
     */
    public boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }
}