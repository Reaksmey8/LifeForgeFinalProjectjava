package com.lifeforge.controller;

import com.lifeforge.Session;
import com.lifeforge.model.*;
import com.lifeforge.service.ExplanationOutcome;
import com.lifeforge.service.AiChatResponse;
import com.lifeforge.service.GoalService;
import com.lifeforge.service.RecommendationService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the recommendation workflow for the current user plus
 * the admin Recommendation/Category CMS operations. Generation always
 * requires a selected goal, which is resolved through GoalService for
 * the session user.
 */
public class RecommendationController extends BaseController {

    private final RecommendationService recommendationService;
    private final GoalService goalService;
    private final Session session;

    public RecommendationController(RecommendationService recommendationService,
                                    GoalService goalService, Session session) {
        this.recommendationService = recommendationService;
        this.goalService = goalService;
        this.session = session;
    }

    public Optional<Goal> currentGoal() {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return Optional.empty();
        }
        try {
            return goalService.getCurrentGoalForUser(current.getId());
        } catch (SQLException e) {
            setError(e, "Failed to load your current goal.");
            return Optional.empty();
        }
    }

    public Optional<RecommendationService.RecommendationResult> generate(Long categoryId) {
        Optional<Goal> goalOpt = currentGoal();
        if (goalOpt.isEmpty()) {
            setError("Select a goal before generating recommendations.");
            return Optional.empty();
        }
        User user = session.getCurrentUser();
        try {
            return recommendationService.getRecommendation(user, goalOpt.get(), categoryId);
        } catch (SQLException e) {
            setError(e, "Failed to generate this recommendation.");
            return Optional.empty();
        }
    }

    public Optional<PersonalizedAnalysisResult> getPersonalizedAnalysis() {
        Optional<Goal> goalOpt = currentGoal();
        if (goalOpt.isEmpty()) {
            setError("Select a goal before viewing personalized analysis.");
            return Optional.empty();
        }
        User user = session.getCurrentUser();
        return Optional.of(recommendationService.getPersonalizedAnalysis(user, goalOpt.get()));
    }

    public Optional<PersonalizedPlanResult> getPersonalizedPlan() {
        Optional<Goal> goalOpt = currentGoal();
        if (goalOpt.isEmpty()) {
            setError("Select a goal before viewing your personalized plan.");
            return Optional.empty();
        }
        User user = session.getCurrentUser();
        try {
            return Optional.of(recommendationService.getPersonalizedPlan(user, goalOpt.get()));
        } catch (SQLException e) {
            setError(e, "Failed to load your personalized plan.");
            return Optional.empty();
        }
    }

    public Optional<CalculationTrace> getCalculationTrace() {
        Optional<Goal> goalOpt = currentGoal();
        if (goalOpt.isEmpty()) {
            setError("Select a goal before viewing calculation trace.");
            return Optional.empty();
        }
        User user = session.getCurrentUser();
        return Optional.of(recommendationService.getCalculationTrace(user, goalOpt.get()));
    }

    public Optional<DailyBlueprint> getDailyBlueprint() {
        Optional<Goal> goalOpt = currentGoal();
        if (goalOpt.isEmpty()) {
            setError("Select a goal before viewing the daily blueprint.");
            return Optional.empty();
        }
        User user = session.getCurrentUser();
        return Optional.of(recommendationService.getDailyBlueprint(user, goalOpt.get()));
    }

    /**
     * Computes the "Why This Recommendation?" explanation for an
     * already-generated recommendation, on demand. Tries AI first
     * (if configured), safely falls back to the rule-based
     * explanation on any AI failure - see AiExplanationService.
     */
    public Optional<ExplanationOutcome> explainWhyThisFits(Recommendation recommendation) {
        if (recommendation == null) {
            setError("No recommendation to explain.");
            return Optional.empty();
        }
        Optional<Goal> goalOpt = currentGoal();
        if (goalOpt.isEmpty()) {
            setError("Select a goal before requesting an explanation.");
            return Optional.empty();
        }
        User user = session.getCurrentUser();
        if (user == null) {
            setError("Not logged in.");
            return Optional.empty();
        }
        return Optional.of(recommendationService.explainWhyThisFits(user, goalOpt.get(), recommendation));
    }

    /** Sends an optional AI chat question with the already-selected context. */
    public Optional<AiChatResponse> chatWithAi(RecommendationCategory category,
                                                Recommendation recommendation,
                                                List<String> recentConversation,
                                                String question) {
        if (recommendation == null) {
            setError("No LifeForge recommendation is available for AI assistance.");
            return Optional.empty();
        }
        Optional<Goal> goalOpt = currentGoal();
        User user = session.getCurrentUser();
        if (goalOpt.isEmpty() || user == null) {
            if (user == null) {
                setError("Not logged in.");
            }
            return Optional.empty();
        }
        return Optional.of(recommendationService.chatWithAi(
                user, goalOpt.get(), category, recommendation, recentConversation, question));
    }

    /** Sends an AI question to the Global Assistant using the active user's plan. */
    public Optional<AiChatResponse> chatWithGlobalAssistant(List<String> recentConversation,
                                                            String question) {
        Optional<Goal> goalOpt = currentGoal();
        User user = session.getCurrentUser();
        if (user == null) {
            setError("Not logged in.");
            return Optional.empty();
        }
        try {
            return Optional.of(recommendationService.chatWithGlobalAssistant(
                    user, goalOpt.orElse(null), recentConversation, question));
        } catch (SQLException e) {
            setError(e, "Failed to load plan for AI assistance.");
            return Optional.empty();
        }
    }

    public List<RecommendationCategory> getTopCategories() {
        try {
            return recommendationService.getTopLevelCategories();
        } catch (SQLException e) {
            setError(e, "Failed to load recommendation categories.");
            return null;
        }
    }

    public List<RecommendationCategory> getSubCategories(Long parentCategoryId) {
        try {
            return recommendationService.getSubCategories(parentCategoryId);
        } catch (SQLException e) {
            setError(e, "Failed to load subcategories.");
            return null;
        }
    }

    public List<RecommendationCategory> listAllCategories() {
        try {
            return recommendationService.listAllCategories();
        } catch (SQLException e) {
            setError(e, "Failed to load categories.");
            return null;
        }
    }

    public List<Recommendation> listAllRecommendations() {
        try {
            return recommendationService.listAllRecommendations();
        } catch (SQLException e) {
            setError(e, "Failed to load recommendations.");
            return null;
        }
    }

    public Optional<Recommendation> findRecommendationById(Long id) {
        try {
            return recommendationService.findRecommendationById(id);
        } catch (SQLException e) {
            setError(e, "Failed to load this recommendation.");
            return Optional.empty();
        }
    }

    public boolean createRecommendation(Recommendation recommendation) {
        try {
            recommendationService.createRecommendation(recommendation);
            return true;
        } catch (SQLException e) {
            setError(e, "Failed to create the recommendation.");
            return false;
        }
    }

    public boolean updateRecommendation(Recommendation recommendation) {
        try {
            recommendationService.updateRecommendation(recommendation);
            return true;
        } catch (SQLException e) {
            setError(e, "Failed to update the recommendation.");
            return false;
        }
    }

    public boolean deleteRecommendation(Long id) {
        try {
            recommendationService.deleteRecommendation(id);
            return true;
        } catch (SQLException e) {
            setError(e, "Failed to delete the recommendation.");
            return false;
        }
    }

    public boolean createCategory(RecommendationCategory category) {
        try {
            recommendationService.createCategory(category);
            return true;
        } catch (SQLException e) {
            setError(e, "Failed to create the category.");
            return false;
        }
    }

    public boolean updateCategory(RecommendationCategory category) {
        try {
            recommendationService.updateCategory(category);
            return true;
        } catch (SQLException e) {
            setError(e, "Failed to update the category.");
            return false;
        }
    }

    public boolean deleteCategory(Long id) {
        try {
            recommendationService.deleteCategory(id);
            return true;
        } catch (SQLException e) {
            setError(e, "Failed to delete the category.");
            return false;
        }
    }
}
