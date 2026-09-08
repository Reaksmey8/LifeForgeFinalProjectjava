package com.lifeforge.model;

/**
 * User account role. Determines dashboard routing after login.
 */
public enum Role {
    USER,
    ADMIN;

    public static Role fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Role value cannot be null.");
        }
        return Role.valueOf(value.trim().toUpperCase());
    }
}