package com.lifeforge.service;

import com.lifeforge.model.User;
import com.lifeforge.util.HydrationCalculator;

/**
 * Produces the suggested daily hydration figure for a user's profile.
 * Recommendation-level only - never a water-logging feature.
 */
public class HydrationService {

    public double suggestedLitersPerDay(User user) {
        return HydrationCalculator.suggestedLitersPerDay(user.getWeightKg(), user.getActivityLevel());
    }
}