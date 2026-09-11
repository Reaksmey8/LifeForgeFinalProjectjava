package com.lifeforge.model;

import com.lifeforge.service.CalorieService;

import java.util.List;

/**
 * Result bundle for the Personalized Analysis screen, containing user profile
 * baseline, focus areas, estimated targets from CalorieService and HydrationService,
 * and the deterministic MatchScoreBreakdown.
 */
public class PersonalizedAnalysisResult {

    private final User user;
    private final Goal goal;
    private final List<String> primaryFocusAreas;
    private final List<String> supportingAreas;
    private final CalorieService.CalorieSummary calorieSummary;
    private final boolean calorieRelevant;
    private final double suggestedHydrationLiters;
    private final MatchScoreBreakdown matchScore;

    public PersonalizedAnalysisResult(User user,
                                      Goal goal,
                                      List<String> primaryFocusAreas,
                                      List<String> supportingAreas,
                                      CalorieService.CalorieSummary calorieSummary,
                                      boolean calorieRelevant,
                                      double suggestedHydrationLiters,
                                      MatchScoreBreakdown matchScore) {
        this.user = user;
        this.goal = goal;
        this.primaryFocusAreas = primaryFocusAreas;
        this.supportingAreas = supportingAreas;
        this.calorieSummary = calorieSummary;
        this.calorieRelevant = calorieRelevant;
        this.suggestedHydrationLiters = suggestedHydrationLiters;
        this.matchScore = matchScore;
    }

    public User getUser() {
        return user;
    }

    public Goal getGoal() {
        return goal;
    }

    public List<String> getPrimaryFocusAreas() {
        return primaryFocusAreas;
    }

    public List<String> getSupportingAreas() {
        return supportingAreas;
    }

    public CalorieService.CalorieSummary getCalorieSummary() {
        return calorieSummary;
    }

    public boolean isCalorieRelevant() {
        return calorieRelevant;
    }

    public double getSuggestedHydrationLiters() {
        return suggestedHydrationLiters;
    }

    public MatchScoreBreakdown getMatchScore() {
        return matchScore;
    }

    public GoalCompatibilityStatus getCompatibilityStatus() {
        return matchScore != null ? matchScore.getCompatibilityStatus() : null;
    }
}
