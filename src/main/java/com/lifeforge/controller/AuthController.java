package com.lifeforge.controller;

import com.lifeforge.Session;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.service.AuthService;

/**
 * Authentication operations (register / login / logout). On success
 * the authenticated user is placed into the shared Session so all
 * screens can read the current user without re-querying.
 */
public class AuthController extends BaseController {

    private final AuthService authService;
    private final Session session;

    public AuthController(AuthService authService, Session session) {
        this.authService = authService;
        this.session = session;
    }

    public boolean register(String fullName, String email, String password, String confirmPassword,
                            Integer age, Gender gender, Double heightCm, Double weightKg,
                            ActivityLevel activityLevel) {
        AuthService.RegistrationResult result = authService.register(
                fullName, email, password, confirmPassword, age, gender, heightCm, weightKg, activityLevel);
        if (!result.success) {
            setError(result.errorMessage);
            return false;
        }
        session.setCurrentUser(result.user);
        return true;
    }

    public boolean login(String login, String password) {
        AuthService.LoginResult result = authService.login(login, password);
        if (!result.success) {
            setError(result.errorMessage);
            return false;
        }
        session.setCurrentUser(result.user);
        return true;
    }

    public void logout() {
        session.clear();
    }

    public boolean isAdmin() {
        return authService.isAdmin(session.getCurrentUser());
    }
}