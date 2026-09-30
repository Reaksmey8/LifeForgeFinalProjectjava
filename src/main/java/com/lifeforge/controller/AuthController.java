package com.lifeforge.controller;

import com.lifeforge.Session;
import com.lifeforge.dto.UserLoginDto;
import com.lifeforge.dto.UserRegistrationDto;
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

    public boolean register(UserRegistrationDto dto) {
        if (dto == null) {
            setError("Registration details cannot be null.");
            return false;
        }
        return register(dto.getUsername(), dto.getEmail(), dto.getPassword(), dto.getConfirmPassword(),
                dto.getAge(), dto.getGender(), dto.getHeightCm(), dto.getWeightKg(), dto.getActivityLevel());
    }

    public boolean register(String username, String email, String password, String confirmPassword,
                            Integer age, Gender gender, Double heightCm, Double weightKg,
                            ActivityLevel activityLevel) {
        AuthService.RegistrationResult result = authService.register(
                username, email, password, confirmPassword, age, gender, heightCm, weightKg, activityLevel);
        if (!result.success) {
            setError(result.errorMessage);
            return false;
        }
        session.setCurrentUser(result.user);
        return true;
    }

    public boolean login(UserLoginDto dto) {
        if (dto == null) {
            setError("Login credentials cannot be null.");
            return false;
        }
        return login(dto.getLogin(), dto.getPassword());
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