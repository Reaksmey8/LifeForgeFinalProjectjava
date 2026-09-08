package com.lifeforge.service;

import com.lifeforge.model.Goal;
import com.lifeforge.model.User;
import com.lifeforge.util.CalorieCalculator;

/**
 * Produces recommendation-level calorie figures (BMR, TDEE, suggested
 * target) for a user's current profile and goal. These are guidance
 * numbers only - the application never asks the user to log intake.
 */
public class CalorieService {

    public static class CalorieSummary {
        public final double bmr;
        public final double tdee;
        public final double suggestedTarget;

        public CalorieSummary(double bmr, double tdee, double suggestedTarget) {
            this.bmr = bmr;
            this.tdee = tdee;
            this.suggestedTarget = suggestedTarget;
        }
    }

    public CalorieSummary calculateFor(User user, Goal goal) {
        double bmr = CalorieCalculator.calculateBmr(
                user.getGender(), user.getWeightKg(), user.getHeightCm(), user.getAge());
        double tdee = CalorieCalculator.calculateTdee(bmr, user.getActivityLevel());

        double adjustment = goalAdjustment(goal);
        double target = CalorieCalculator.suggestedCalorieTarget(tdee, adjustment);

        return new CalorieSummary(round(bmr), round(tdee), round(target));
    }

    private double goalAdjustment(Goal goal) {
        if (goal == null || goal.getCode() == null) {
            return 0;
        }
        return switch (goal.getCode()) {
            case "LOSE_WEIGHT" -> -500;
            case "GAIN_WEIGHT" -> 400;
            case "BUILD_MUSCLE" -> 300;
            default -> 0; // Improve Fitness / Skin Health / Sleep / General Wellness / other -> maintenance
        };
    }

    /**
     * Whether a calorie target is actually meaningful for this goal.
     * Only weight-related goals (lose/gain weight, build muscle) get
     * a calorie adjustment above (see goalAdjustment) - every other
     * goal, including any admin-added custom goal such as "Posture
     * Correction", just resolves to maintenance calories, which is
     * not useful information for that goal and should not be shown.
     */
    public boolean isWeightRelevantGoal(Goal goal) {
        if (goal == null || goal.getCode() == null) {
            return false;
        }
        return switch (goal.getCode()) {
            case "LOSE_WEIGHT", "GAIN_WEIGHT", "BUILD_MUSCLE" -> true;
            default -> false;
        };
    }

    private double round(double value) {
        return Math.round(value / 10.0) * 10.0; // round to nearest 10 kcal
    }
}