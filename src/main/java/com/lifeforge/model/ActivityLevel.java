package com.lifeforge.model;

/**
 * User's general activity level. Each level carries a TDEE
 * multiplier used by CalorieService, and is also used directly
 * by the RecommendationEngine's rule matching.
 */
public enum ActivityLevel {
    SEDENTARY(1.2, "Little or no exercise"),
    LIGHTLY_ACTIVE(1.375, "Light exercise 1-3 days/week"),
    MODERATELY_ACTIVE(1.55, "Moderate exercise 3-5 days/week"),
    VERY_ACTIVE(1.725, "Hard exercise 6-7 days/week"),
    EXTRA_ACTIVE(1.9, "Very hard exercise, physical job, or training twice a day");

    private final double tdeeMultiplier;
    private final String description;

    ActivityLevel(double tdeeMultiplier, String description) {
        this.tdeeMultiplier = tdeeMultiplier;
        this.description = description;
    }

    public double getTdeeMultiplier() {
        return tdeeMultiplier;
    }

    public String getDescription() {
        return description;
    }

    public static ActivityLevel fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Activity level value cannot be null.");
        }
        return ActivityLevel.valueOf(value.trim().toUpperCase());
    }
}