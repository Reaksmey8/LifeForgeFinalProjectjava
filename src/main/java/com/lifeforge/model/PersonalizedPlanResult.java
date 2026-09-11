package com.lifeforge.model;

import java.util.List;

/**
 * Result bundle for the Personalized Plan screen, combining goal context,
 * personalized focus narrative, deterministic match score, and prioritized
 * recommendation areas.
 */
public class PersonalizedPlanResult {

    public record AreaItem(
            RecommendationCategory category,
            String emoji,
            RecommendationPriority priority,
            Recommendation recommendation
    ) {}

    private final Goal goal;
    private final ActivityLevel activityLevel;
    private final String personalizedFocus;
    private final MatchScoreBreakdown matchScore;
    private final List<AreaItem> areas;

    public PersonalizedPlanResult(Goal goal,
                                  ActivityLevel activityLevel,
                                  String personalizedFocus,
                                  MatchScoreBreakdown matchScore,
                                  List<AreaItem> areas) {
        this.goal = goal;
        this.activityLevel = activityLevel;
        this.personalizedFocus = personalizedFocus;
        this.matchScore = matchScore;
        this.areas = areas;
    }

    public Goal getGoal() {
        return goal;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public String getPersonalizedFocus() {
        return personalizedFocus;
    }

    public MatchScoreBreakdown getMatchScore() {
        return matchScore;
    }

    public GoalCompatibilityStatus getCompatibilityStatus() {
        return matchScore != null ? matchScore.getCompatibilityStatus() : null;
    }

    public List<AreaItem> getAreas() {
        return areas;
    }
}
