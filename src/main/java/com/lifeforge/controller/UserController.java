package com.lifeforge.controller;

import com.lifeforge.Session;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.model.User;
import com.lifeforge.service.UserService;

import java.sql.SQLException;
import java.util.Optional;

/**
 * User profile operations (READ/UPDATE/DELETE). CREATE is handled by
 * AuthController during registration. The in-memory Session user is
 * refreshed after a successful profile update so downstream screens
 * (dashboard, recommendation calculations) always see fresh data.
 */
public class UserController extends BaseController {

    private final UserService userService;
    private final Session session;

    public UserController(UserService userService, Session session) {
        this.userService = userService;
        this.session = session;
    }

    public Optional<User> getProfile() {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return Optional.empty();
        }
        try {
            return userService.getProfile(current.getId());
        } catch (SQLException e) {
            setError(e, "Failed to load your profile.");
            return Optional.empty();
        }
    }

    public boolean updateProfile(String fullName, Integer age, Gender gender,
                                 Double heightCm, Double weightKg, ActivityLevel activityLevel) {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return false;
        }
        UserService.UpdateResult result = userService.updateProfile(
                current.getId(), fullName, age, gender, heightCm, weightKg, activityLevel);
        if (!result.success) {
            setError(result.errorMessage);
            return false;
        }
        refreshSessionUser();
        return true;
    }

    public boolean changePassword(String currentPassword, String newPassword, String confirmNewPassword) {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return false;
        }
        UserService.UpdateResult result =
                userService.changePassword(current.getId(), currentPassword, newPassword, confirmNewPassword);
        if (!result.success) {
            setError(result.errorMessage);
            return false;
        }
        return true;
    }

    public boolean deleteAccount() {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return false;
        }
        UserService.UpdateResult result = userService.deleteAccount(current.getId());
        if (!result.success) {
            setError(result.errorMessage);
            return false;
        }
        session.clear();
        return true;
    }

    private void refreshSessionUser() {
        User current = session.getCurrentUser();
        if (current == null) {
            return;
        }
        try {
            userService.getProfile(current.getId()).ifPresent(session::setCurrentUser);
        } catch (SQLException e) {
            // Keep the stale copy; the next profile fetch will retry.
        }
    }
}