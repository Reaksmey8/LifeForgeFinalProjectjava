package com.lifeforge.service;

import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.User;

import java.util.Locale;

/**
 * Always-available, pure-Java explanation generator. This is the
 * REQUIRED explanation path - the application must be fully
 * functional using only this class, with no external dependency.
 */
public class RuleBasedExplanationService implements RecommendationExplanationService {

    @Override
    public ExplanationOutcome explain(User user, Goal goal, ActivityLevel activityLevel, Recommendation recommendation) {
        StringBuilder sb = new StringBuilder();
        String title = recommendation != null ? recommendation.getTitle() : "";
        String desc = recommendation != null ? recommendation.getDescription() : "";
        String combined = (title + " " + desc).toLowerCase(Locale.ROOT);

        if (combined.contains("master") || (combined.contains("routine") && !combined.contains("exercise") && !combined.contains("workout") && !combined.contains("training") && !combined.contains("cardio") && !combined.contains("strength") && !combined.contains("sleep"))) {
            sb.append("• Behavioral Anchoring : Low-friction routines minimize willpower depletion and anchor automatic behavioral loops in your daily schedule.\n");
            sb.append("• Systemic Compounding : Consistent daily execution produces compounding physiological adaptations without inducing acute burnout.\n");
            sb.append("• Lifestyle Synergy : Harmonizing daily habits with your active goal creates sustained momentum across all foundational health pillars.");
        } else if (combined.contains("protein") || combined.contains("nutrition")) {
            String goalName = (goal != null && goal.getName() != null && !goal.getName().isBlank())
                    ? goal.getName()
                    : "lifestyle";
            String actName = (activityLevel != null)
                    ? activityLevel.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    : "current";
            sb.append("• Supports Your Goal : Your daily energy and nutrition guidance directly supports your ").append(goalName).append(" goal.\n");
            sb.append("• Fits Your Activity Level : Your calibrated target is matched to your ").append(actName).append(" activity level.\n");
            sb.append("• Supports Balanced Nutrition : Pairing your energy target with diverse food sources promotes a balanced, sustainable diet.");
        } else if (combined.contains("exercise") || combined.contains("workout") || combined.contains("training")) {
            String goalName = (goal != null && goal.getName() != null && !goal.getName().isBlank())
                    ? goal.getName()
                    : "lifestyle";
            String actName = (activityLevel != null)
                    ? activityLevel.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    : "current";
            sb.append("• Supports Your Goal : Your scheduled training sessions directly support your ").append(goalName).append(" goal.\n");
            sb.append("• Fits Your Activity Level : Your exercise parameters are matched to your ").append(actName).append(" activity level.\n");
            sb.append("• Supports Progressive Training : Consistent, graduated sessions build lasting fitness without excessive fatigue.");
        } else if (combined.contains("hydration") || combined.contains("water")) {
            sb.append("• Cellular Transport : Maintaining steady fluid intake preserves plasma volume, facilitating efficient nutrient delivery and waste clearance across cell membranes.\n");
            sb.append("• Thermoregulation : Proper hydration optimizes internal core temperature control and cushions synovial joint structures during physical activity.\n");
            sb.append("• Cognitive Stamina : Steady daytime fluid intake stabilizes central nervous system focus, cognitive stamina, and metabolic efficiency.");
        } else if (combined.contains("sleep") || combined.contains("recovery")) {
            String goalName = (goal != null && goal.getName() != null && !goal.getName().isBlank())
                    ? goal.getName()
                    : "lifestyle";
            String actName = (activityLevel != null)
                    ? activityLevel.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    : "current";
            sb.append("• Supports Recovery : Quality sleep supports recovery and a consistent lifestyle routine.\n");
            sb.append("• Supports Your Goal : Adequate rest complements your ").append(goalName).append(" goal and lifestyle plan.\n");
            sb.append("• Fits Your Activity Level : A consistent sleep routine supports recovery from your ").append(actName).append(" activity level.");
        } else {
            sb.append("• Behavioral Anchoring : Low-friction routines minimize willpower depletion and anchor automatic behavioral loops in your daily schedule.\n");
            sb.append("• Systemic Compounding : Consistent daily execution produces compounding physiological adaptations without inducing acute burnout.\n");
            sb.append("• Lifestyle Synergy : Harmonizing daily habits with your active goal creates sustained momentum across all foundational health pillars.");
        }

        return new ExplanationOutcome(sb.toString(), false);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}