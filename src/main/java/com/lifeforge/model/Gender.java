package com.lifeforge.model;

/**
 * Biological sex used for BMR/TDEE calculations.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER;

    public static Gender fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Gender value cannot be null.");
        }
        return Gender.valueOf(value.trim().toUpperCase());
    }
}