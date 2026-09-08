package com.lifeforge.util;

import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;

/**
 * Pure calculation helper for BMR and TDEE using the
 * Mifflin-St Jeor equation. These are recommendation-level
 * estimates only, not tracking figures.
 */
public final class CalorieCalculator {

    private CalorieCalculator() {
    }

    /**
     * Basal Metabolic Rate (kcal/day) via Mifflin-St Jeor.
     */
    public static double calculateBmr(Gender gender, double weightKg, double heightCm, int age) {
        double base = (10 * weightKg) + (6.25 * heightCm) - (5 * age);
        return switch (gender) {
            case MALE -> base + 5;
            case FEMALE -> base - 161;
            case OTHER -> base - 78; // midpoint approximation
        };
    }

    /**
     * Total Daily Energy Expenditure (kcal/day): BMR scaled by activity level.
     */
    public static double calculateTdee(double bmr, ActivityLevel activityLevel) {
        return bmr * activityLevel.getTdeeMultiplier();
    }

    /**
     * Suggested daily calorie target given a goal adjustment.
     * A positive adjustment (e.g. +300) suggests a surplus for
     * weight/muscle gain; a negative adjustment (e.g. -500)
     * suggests a deficit for weight loss.
     */
    public static double suggestedCalorieTarget(double tdee, double goalAdjustment) {
        double target = tdee + goalAdjustment;
        return Math.max(target, 1200.0); // safety floor
    }
}