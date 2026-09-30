package com.lifeforge.util;

import com.lifeforge.model.ActivityLevel;

/**
 * Pure calculation helper for suggested daily hydration.
 * This is a recommendation only - the user is never required
 * to log actual water consumption.
 */
public final class HydrationCalculator {

    public static final double BASE_ML_PER_KG = 33.0;

    private HydrationCalculator() {
    }

    public static double baseMl(double weightKg) {
        return weightKg * BASE_ML_PER_KG;
    }

    public static double activityBonusMl(ActivityLevel activityLevel) {
        if (activityLevel == null) return 0;
        return switch (activityLevel) {
            case SEDENTARY -> 0;
            case LIGHTLY_ACTIVE -> 250;
            case MODERATELY_ACTIVE -> 500;
            case VERY_ACTIVE -> 750;
            case EXTRA_ACTIVE -> 1000;
        };
    }

    /**
     * Suggested daily water intake in liters, based on body weight
     * and a small activity-level adjustment.
     */
    public static double suggestedLitersPerDay(double weightKg, ActivityLevel activityLevel) {
        double totalMl = baseMl(weightKg) + activityBonusMl(activityLevel);
        return Math.round((totalMl / 1000.0) * 10.0) / 10.0;
    }
}