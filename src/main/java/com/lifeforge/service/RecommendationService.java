package com.lifeforge.service;

import com.lifeforge.dao.RecommendationCategoryDao;
import com.lifeforge.dao.RecommendationDao;
import com.lifeforge.model.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the full recommendation workflow:
 * RecommendationEngine (rules) -> CalorieService/HydrationService
 * (calculations). The "Why This Recommendation?" explanation is
 * NOT computed here automatically - it is computed on demand via
 * explainWhyThisFits(), only when the user actually asks for it.
 *
 * This is the class controllers/TUI screens call - they never talk
 * to RecommendationEngine or the explanation services directly.
 */
public class RecommendationService {

    private final RecommendationEngine recommendationEngine;
    private final RecommendationCategoryDao categoryDao;
    private final RecommendationDao recommendationDao;
    private final CalorieService calorieService;
    private final HydrationService hydrationService;
    private final RecommendationExplanationService aiExplanationService;
    private final RecommendationExplanationService ruleBasedExplanationService;

    public RecommendationService(RecommendationEngine recommendationEngine,
                                 RecommendationCategoryDao categoryDao,
                                 RecommendationDao recommendationDao,
                                 CalorieService calorieService,
                                 HydrationService hydrationService,
                                 RecommendationExplanationService aiExplanationService,
                                 RecommendationExplanationService ruleBasedExplanationService) {
        this.recommendationEngine = recommendationEngine;
        this.categoryDao = categoryDao;
        this.recommendationDao = recommendationDao;
        this.calorieService = calorieService;
        this.hydrationService = hydrationService;
        this.aiExplanationService = aiExplanationService;
        this.ruleBasedExplanationService = ruleBasedExplanationService;
    }

    /**
     * Result bundle shown on the Recommendation Detail screen.
     * Does NOT include the "why this fits" explanation - that is
     * fetched separately, on demand, via explainWhyThisFits().
     */
    public static class RecommendationResult {
        public final Recommendation recommendation;
        public final CalorieService.CalorieSummary calorieSummary;
        public final double suggestedHydrationLiters;
        public final boolean calorieRelevant;

        public RecommendationResult(Recommendation recommendation,
                                    CalorieService.CalorieSummary calorieSummary,
                                    double suggestedHydrationLiters,
                                    boolean calorieRelevant) {
            this.recommendation = recommendation;
            this.calorieSummary = calorieSummary;
            this.suggestedHydrationLiters = suggestedHydrationLiters;
            this.calorieRelevant = calorieRelevant;
        }
    }

    public List<RecommendationCategory> getTopLevelCategories() throws SQLException {
        return categoryDao.findTopLevel();
    }

    public List<RecommendationCategory> getSubCategories(Long parentCategoryId) throws SQLException {
        return categoryDao.findChildren(parentCategoryId);
    }

    /**
     * Generates a complete recommendation result for a user/goal/category,
     * including personalized calculations. No AI/explanation call happens
     * here - that only happens if/when the user selects
     * "Why This Recommendation?" (see explainWhyThisFits below).
     */
    public Optional<RecommendationResult> getRecommendation(
            User user,
            Goal goal,
            Long categoryId
    ) throws SQLException {

        Optional<Recommendation> baseRecommendation =
                recommendationEngine.generateBaseRecommendation(
                        user,
                        goal,
                        categoryId
                );

        if (baseRecommendation.isEmpty()) {
            return Optional.empty();
        }

        Recommendation recommendation = baseRecommendation.get();

        CalorieService.CalorieSummary calorieSummary =
                calorieService.calculateFor(user, goal);

        double hydration =
                hydrationService.suggestedLitersPerDay(user);

        boolean calorieRelevant = calorieService.isWeightRelevantGoal(goal);

        return Optional.of(
                new RecommendationResult(
                        recommendation,
                        calorieSummary,
                        hydration,
                        calorieRelevant
                )
        );
    }

    /**
     * Computes the "Why This Recommendation?" explanation on demand.
     * Tries AI first if configured; AiExplanationService itself
     * guarantees a safe fallback to the rule-based explanation on
     * any failure, and reports which one was actually used via
     * ExplanationOutcome.fromAi.
     */

    public ExplanationOutcome explainWhyThisFits(
            User user,
            Goal goal,
            Recommendation recommendation) {

        return aiExplanationService.explain(
                user,
                goal,
                user.getActivityLevel(),
                recommendation
        );
    }

    /** Optional contextual chat; it is separate from recommendation generation. */
    public AiChatResponse chatWithAi(User user, Goal goal, RecommendationCategory category,
                                     Recommendation recommendation, List<String> recentConversation,
                                     String question) {
        if (aiExplanationService instanceof AiExplanationService aiService) {
            return aiService.chat(user, goal, category, recommendation, recentConversation, question);
        }
        return new AiChatResponse("AI is currently unavailable. Your official LifeForge "
                + "recommendation is still available.", false);
    }

    public Optional<Recommendation> findRecommendationById(Long id) throws SQLException {
        return recommendationDao.findById(id);
    }

    // ---- Admin / Recommendation CMS operations ----

    public List<Recommendation> listAllRecommendations() throws SQLException {
        return recommendationDao.findAll();
    }

    public Recommendation createRecommendation(Recommendation recommendation) throws SQLException {
        return recommendationDao.create(recommendation);
    }

    public void updateRecommendation(Recommendation recommendation) throws SQLException {
        recommendationDao.update(recommendation);
    }

    public void deleteRecommendation(Long id) throws SQLException {
        recommendationDao.delete(id);
    }

    public List<RecommendationCategory> listAllCategories() throws SQLException {
        return categoryDao.findAll();
    }

    public RecommendationCategory createCategory(RecommendationCategory category) throws SQLException {
        return categoryDao.create(category);
    }

    public void updateCategory(RecommendationCategory category) throws SQLException {
        categoryDao.update(category);
    }

    public void deleteCategory(Long id) throws SQLException {
        categoryDao.delete(id);
    }
}
