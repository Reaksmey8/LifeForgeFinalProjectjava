package com.lifeforge.service;

import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.User;

/**
 * Always-available, pure-Java explanation generator. This is the
 * REQUIRED explanation path - the application must be fully
 * functional using only this class, with no external dependency.
 */
public class RuleBasedExplanationService implements RecommendationExplanationService {

    @Override
    public ExplanationOutcome explain(User user, Goal goal, ActivityLevel activityLevel, Recommendation recommendation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Because your selected goal is ").append(goal.getName().toLowerCase())
                .append(" and your current activity level is ")
                .append(readableActivityLevel(activityLevel)).append(", ");
        sb.append("LifeForge recommends: ").append(recommendation.getTitle()).append(". ");

        if (recommendation.getSuggestedTarget() != null && !recommendation.getSuggestedTarget().isBlank()) {
            sb.append("The suggested target of ").append(recommendation.getSuggestedTarget())
                    .append(" is calibrated to your profile (age ").append(user.getAge())
                    .append(", weight ").append(formatWeight(user.getWeightKg())).append("). ");
        }

        sb.append("This recommendation is intended as a general guideline, not a rigid daily requirement.");
        return new ExplanationOutcome(sb.toString(), false);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private String readableActivityLevel(ActivityLevel level) {
        return level.name().toLowerCase().replace('_', ' ');
    }

    private String formatWeight(double weightKg) {
        return String.format("%.1f kg", weightKg);
    }
}