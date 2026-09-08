package com.lifeforge.util;

import com.lifeforge.config.AppConfig;

import java.util.regex.Pattern;

/**
 * Centralized input validation. Every method returns null when
 * the value is valid, or a friendly error message when it is not,
 * so TUI screens can display validation feedback consistently.
 */
public final class ValidationUtil {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private ValidationUtil() {
    }

    public static String validateFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "Full name is required.";
        }
        if (fullName.trim().length() < 2) {
            return "Full name must be at least 2 characters.";
        }
        if (fullName.trim().length() > 100) {
            return "Full name must be under 100 characters.";
        }
        return null;
    }

    public static String validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "Email is required.";
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return "Please enter a valid email address.";
        }
        return null;
    }

    public static String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password is required.";
        }
        if (password.length() < AppConfig.MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + AppConfig.MIN_PASSWORD_LENGTH + " characters.";
        }
        return null;
    }

    public static String validatePasswordConfirmation(String password, String confirmPassword) {
        if (confirmPassword == null || !confirmPassword.equals(password)) {
            return "Passwords do not match.";
        }
        return null;
    }

    public static String validateAge(Integer age) {
        if (age == null) {
            return "Age is required.";
        }
        if (age < AppConfig.MIN_AGE || age > AppConfig.MAX_AGE) {
            return "Age must be between " + AppConfig.MIN_AGE + " and " + AppConfig.MAX_AGE + ".";
        }
        return null;
    }

    public static String validateHeight(Double heightCm) {
        if (heightCm == null) {
            return "Height is required.";
        }
        if (heightCm < AppConfig.MIN_HEIGHT_CM || heightCm > AppConfig.MAX_HEIGHT_CM) {
            return "Height must be between " + AppConfig.MIN_HEIGHT_CM + " and "
                    + AppConfig.MAX_HEIGHT_CM + " cm.";
        }
        return null;
    }

    public static String validateWeight(Double weightKg) {
        if (weightKg == null) {
            return "Weight is required.";
        }
        if (weightKg < AppConfig.MIN_WEIGHT_KG || weightKg > AppConfig.MAX_WEIGHT_KG) {
            return "Weight must be between " + AppConfig.MIN_WEIGHT_KG + " and "
                    + AppConfig.MAX_WEIGHT_KG + " kg.";
        }
        return null;
    }

    public static String validateRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return fieldName + " is required.";
        }
        return null;
    }

    public static boolean isValidMenuChoice(String input, int min, int max) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        try {
            int choice = Integer.parseInt(input.trim());
            return choice >= min && choice <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}