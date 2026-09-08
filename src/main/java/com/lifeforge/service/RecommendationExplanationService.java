package com.lifeforge.service;

import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.User;

/**
 * Abstraction for generating a natural-language explanation of why
 * a recommendation fits a user. RuleBasedExplanationService is the
 * always-available default; AiExplanationService is an optional
 * enhancement. The core RecommendationEngine never depends directly
 * on AI - only on this interface - so AI can be swapped or removed
 * without touching core recommendation logic.
 */
public interface RecommendationExplanationService {

    ExplanationOutcome explain(User user, Goal goal, ActivityLevel activityLevel, Recommendation recommendation);

    /**
     * Whether this implementation is currently able to produce an
     * explanation (e.g. AI service reachable / API key configured).
     * RuleBasedExplanationService always returns true.
     */
    boolean isAvailable();
}