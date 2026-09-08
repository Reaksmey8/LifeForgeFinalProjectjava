package com.lifeforge.util;

import com.lifeforge.model.ActivityLevel;

/**
 * Pure calculation helper for suggested daily hydration.
 * This is a recommendation only - the user is never required
 * to log actual water consumption.
 */
public final class HydrationCalculator {

    private static final double BASE_ML_PER_KG = 33.0;

    private HydrationCalculator() {
    }

    /**
     * Suggested daily water intake in liters, based on body weight
     * and a small activity-level adjustment.
     */
    public static double suggestedLitersPerDay(double weightKg, ActivityLevel activityLevel) {
        double baseMl = weightKg * BASE_ML_PER_KG;

        double activityBonusMl = switch (activityLevel) {
            case SEDENTARY -> 0;
            case LIGHTLY_ACTIVE -> 250;
            case MODERATELY_ACTIVE -> 500;
            case VERY_ACTIVE -> 750;
            case EXTRA_ACTIVE -> 1000;
        };

        double totalMl = baseMl + activityBonusMl;
        return Math.round((totalMl / 1000.0) * 10.0) / 10.0;
    }
}