package com.lifeforge.model;

import com.lifeforge.service.CalorieService;
import com.lifeforge.util.CalorieCalculator;
import com.lifeforge.util.HydrationCalculator;

/**
 * Academic transparency model that proves what the real application calculates.
 * Reuses CalorieCalculator and HydrationCalculator directly without introducing
 * independent formulas.
 */
public class CalculationTrace {

    private final User user;
    private final Goal goal;

    // BMR
    private final String bmrFormula;
    private final String bmrSubstitution;
    private final double bmrResult;

    // TDEE
    private final String tdeeFormula;
    private final double activityMultiplier;
    private final String tdeeSubstitution;
    private final double tdeeResult;

    // Calorie Target
    private final boolean weightRelevant;
    private final double calorieAdjustment;
    private final double suggestedCalorieTarget;

    // Hydration
    private final String hydrationFormula;
    private final double hydrationBaseMl;
    private final double hydrationBonusMl;
    private final double hydrationTotalMl;
    private final double hydrationLiters;

    // Match Score
    private final MatchScoreBreakdown matchScore;

    public CalculationTrace(User user, Goal goal, CalorieService calorieService, MatchScoreBreakdown matchScore) {
        this.user = user;
        this.goal = goal;
        this.matchScore = matchScore;

        double weight = user != null && user.getWeightKg() != null ? user.getWeightKg() : 70.0;
        double height = user != null && user.getHeightCm() != null ? user.getHeightCm() : 170.0;
        int age = user != null && user.getAge() != null ? user.getAge() : 25;
        Gender gender = user != null && user.getGender() != null ? user.getGender() : Gender.OTHER;
        ActivityLevel activity = user != null && user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.SEDENTARY;

        // BMR via CalorieCalculator.calculateBmr
        this.bmrResult = CalorieCalculator.calculateBmr(gender, weight, height, age);
        String genderOffset = switch (gender) {
            case MALE -> "+ 5";
            case FEMALE -> "- 161";
            case OTHER -> "- 78";
        };
        this.bmrFormula = gender.name() + ": BMR = 10W + 6.25H - 5A " + genderOffset;
        this.bmrSubstitution = String.format("= 10(%.1f) + 6.25(%.1f) - 5(%d) %s\n= %.0f kcal/day",
                weight, height, age, genderOffset, bmrResult);

        // TDEE via CalorieCalculator.calculateTdee
        this.activityMultiplier = activity.getTdeeMultiplier();
        this.tdeeResult = CalorieCalculator.calculateTdee(bmrResult, activity);
        this.tdeeFormula = "TDEE = BMR × Activity Multiplier";
        this.tdeeSubstitution = String.format("= %.0f × %.3f (%s)\n= %.0f kcal/day",
                bmrResult, activityMultiplier, activity.name(), tdeeResult);

        // Calorie target
        this.weightRelevant = calorieService.isWeightRelevantGoal(goal);
        CalorieService.CalorieSummary cs = calorieService.calculateFor(user, goal);
        this.suggestedCalorieTarget = cs.suggestedTarget;
        this.calorieAdjustment = cs.suggestedTarget - cs.tdee;

        // Hydration via HydrationCalculator
        this.hydrationFormula = "Hydration = (Weight × 33.0 mL/kg) + Activity Bonus";
        this.hydrationBaseMl = weight * 33.0;
        this.hydrationBonusMl = switch (activity) {
            case SEDENTARY -> 0;
            case LIGHTLY_ACTIVE -> 250;
            case MODERATELY_ACTIVE -> 500;
            case VERY_ACTIVE -> 750;
            case EXTRA_ACTIVE -> 1000;
        };
        this.hydrationTotalMl = hydrationBaseMl + hydrationBonusMl;
        this.hydrationLiters = HydrationCalculator.suggestedLitersPerDay(weight, activity);
    }

    public User getUser() { return user; }
    public Goal getGoal() { return goal; }
    public String getBmrFormula() { return bmrFormula; }
    public String getBmrSubstitution() { return bmrSubstitution; }
    public double getBmrResult() { return bmrResult; }
    public String getTdeeFormula() { return tdeeFormula; }
    public double getActivityMultiplier() { return activityMultiplier; }
    public String getTdeeSubstitution() { return tdeeSubstitution; }
    public double getTdeeResult() { return tdeeResult; }
    public boolean isWeightRelevant() { return weightRelevant; }
    public double getCalorieAdjustment() { return calorieAdjustment; }
    public double getSuggestedCalorieTarget() { return suggestedCalorieTarget; }
    public String getHydrationFormula() { return hydrationFormula; }
    public double getHydrationBaseMl() { return hydrationBaseMl; }
    public double getHydrationBonusMl() { return hydrationBonusMl; }
    public double getHydrationTotalMl() { return hydrationTotalMl; }
    public double getHydrationLiters() { return hydrationLiters; }
    public MatchScoreBreakdown getMatchScore() { return matchScore; }
}
