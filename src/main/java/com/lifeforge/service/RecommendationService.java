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

        if (categoryId != null) {
            Optional<RecommendationCategory> catOpt = categoryDao.findById(categoryId);
            if (catOpt.isPresent() && isMasterRoutineCategory(catOpt.get())) {
                return Optional.of(buildMasterRoutineResult(user, goal, catOpt.get()));
            }
        }

        Optional<Recommendation> baseRecommendation =
                recommendationEngine.generateBaseRecommendation(
                        user,
                        goal,
                        categoryId
                );

        if (baseRecommendation.isEmpty() && categoryId != null) {
            Optional<RecommendationCategory> catOpt = categoryDao.findById(categoryId);
            if (catOpt.isPresent()) {
                String catName = catOpt.get().getName().toLowerCase(java.util.Locale.ROOT);
                if (catName.contains("habit") || catName.contains("micro")) {
                    Recommendation habRec = buildStandaloneHabitRecommendation(user, goal, catOpt.get());
                    baseRecommendation = Optional.of(habRec);
                } else if (catName.contains("hydration")) {
                    Recommendation hydRec = buildStandaloneHydrationRecommendation(user, goal, catOpt.get());
                    baseRecommendation = Optional.of(hydRec);
                }
            }
        }

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

    public PersonalizedAnalysisResult getPersonalizedAnalysis(User user, Goal goal) {
        List<String> primaryFocus = recommendationEngine.getPrimaryFocusAreas(goal);
        List<String> supporting = recommendationEngine.getSupportingAreas(goal);
        CalorieService.CalorieSummary calorieSummary = calorieService.calculateFor(user, goal);
        boolean calorieRelevant = calorieService.isWeightRelevantGoal(goal);
        double hydration = hydrationService.suggestedLitersPerDay(user);
        MatchScoreBreakdown matchScore = recommendationEngine.computeMatchScore(user, goal);

        return new PersonalizedAnalysisResult(
                user,
                goal,
                primaryFocus,
                supporting,
                calorieSummary,
                calorieRelevant,
                hydration,
                matchScore
        );
    }

    public PersonalizedPlanResult getPersonalizedPlan(User user, Goal goal) throws SQLException {
        MatchScoreBreakdown matchScore = recommendationEngine.computeMatchScore(user, goal);
        String narrative = recommendationEngine.getPersonalizedFocusNarrative(goal, user);

        List<RecommendationCategory> topCats = categoryDao.findTopLevel();
        java.util.List<PersonalizedPlanResult.AreaItem> areaItems = new java.util.ArrayList<>();

        for (RecommendationCategory cat : topCats) {
            String nameLower = cat.getName().toLowerCase();
            if (nameLower.contains("mistake") || nameLower.contains("myth")) {
                continue; // Focus plan on active lifestyle guidance areas
            }

            String emoji = categoryEmoji(cat.getName());
            RecommendationPriority priority = recommendationEngine.resolvePriority(
                    goal, user != null ? user.getActivityLevel() : null, cat);

            Optional<Recommendation> recOpt = recommendationEngine.generateBaseRecommendation(
                    user, goal, cat.getId());

            if (recOpt.isEmpty()) {
                if (isMasterRoutineCategory(cat)) {
                    RecommendationResult mr = buildMasterRoutineResult(user, goal, cat);
                    recOpt = Optional.of(mr.recommendation);
                } else if (nameLower.contains("habit") || nameLower.contains("micro")) {
                    recOpt = Optional.of(buildStandaloneHabitRecommendation(user, goal, cat));
                } else if (nameLower.contains("hydration")) {
                    recOpt = Optional.of(buildStandaloneHydrationRecommendation(user, goal, cat));
                }
            }

            areaItems.add(new PersonalizedPlanResult.AreaItem(
                    cat,
                    emoji,
                    priority,
                    recOpt.orElse(null)
            ));
        }

        // Sort areas deterministically: High priority first, then Recommended, then Supporting
        areaItems.sort(java.util.Comparator
                .comparingInt((PersonalizedPlanResult.AreaItem a) -> a.priority().getRank())
                .thenComparingInt(a -> a.category().getDisplayOrder()));

        return new PersonalizedPlanResult(
                goal,
                user != null ? user.getActivityLevel() : null,
                narrative,
                matchScore,
                areaItems
        );
    }

    public CalculationTrace getCalculationTrace(User user, Goal goal) {
        return recommendationEngine.buildCalculationTrace(user, goal, calorieService);
    }

    public DailyBlueprint getDailyBlueprint(User user, Goal goal) {
        return recommendationEngine.buildDailyBlueprint(user, goal);
    }

    private String categoryEmoji(String categoryName) {
        String lower = categoryName.toLowerCase();
        if (lower.contains("nutrition")) return "\uD83C\uDF4E"; // 🍎
        if (lower.contains("exercise")) return "\uD83C\uDFC3";  // 🏃
        if (lower.contains("hydration")) return "\uD83D\uDCA7"; // 💧
        if (lower.contains("sleep")) return "\uD83D\uDE34";     // 😴
        if (lower.contains("habit")) return "\uD83C\uDF31";     // 🌱
        if (lower.contains("master") || lower.contains("routine")) return "\u2B50"; // ⭐
        return "\uD83C\uDFAF"; // 🎯
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
            PersonalizedPlanResult plan = null;
            if (user != null && goal != null) {
                try {
                    plan = getPersonalizedPlan(user, goal);
                } catch (Exception ignored) {}
            }
            return aiService.chat(user, goal, category, recommendation, plan, recentConversation, question);
        }
        return new AiChatResponse("AI is currently unavailable. Your official LifeForge "
                + "recommendation is still available.", false);
    }

    /** Global AI Assistant on User Dashboard (Mode A). */
    public AiChatResponse chatWithGlobalAssistant(User user, Goal goal, List<String> recentConversation,
                                                  String question) throws SQLException {
        PersonalizedPlanResult plan = (goal != null) ? getPersonalizedPlan(user, goal) : null;
        CalorieService.CalorieSummary cs = calorieService.calculateFor(user, goal);
        double hydration = hydrationService.suggestedLitersPerDay(user);
        boolean calorieRelevant = calorieService.isWeightRelevantGoal(goal);

        if (aiExplanationService instanceof AiExplanationService aiService) {
            return aiService.chatGlobal(user, goal, plan, cs, hydration, calorieRelevant, recentConversation, question);
        }
        return new AiChatResponse("AI is currently unavailable. Your official LifeForge plan is still available.", false);
    }

    /** Clears the in-memory multi-turn conversation history with the AI Assistant. */
    public void resetAiConversation() {
        if (aiExplanationService instanceof AiExplanationService aiService) {
            aiService.clearConversationHistory();
        }
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

    public boolean isMasterRoutineCategory(RecommendationCategory cat) {
        if (cat == null) return false;
        String name = cat.getName() != null ? cat.getName().toLowerCase(java.util.Locale.ROOT) : "";
        return name.contains("master") || name.contains("routine");
    }

    public RecommendationResult buildMasterRoutineResult(User user, Goal goal, RecommendationCategory masterCategory)
            throws SQLException {

        // Query top-level categories to locate the 5 lifestyle guidance pillars
        List<RecommendationCategory> topCats = categoryDao.findTopLevel();
        RecommendationCategory nutritionCat = null;
        RecommendationCategory exerciseCat = null;
        RecommendationCategory hydrationCat = null;
        RecommendationCategory sleepCat = null;
        RecommendationCategory habitCat = null;

        for (RecommendationCategory c : topCats) {
            String lower = c.getName().toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("nutrition")) {
                nutritionCat = c;
            } else if (lower.contains("exercise")) {
                exerciseCat = c;
            } else if (lower.contains("hydration")) {
                hydrationCat = c;
            } else if (lower.contains("sleep") || lower.contains("recovery")) {
                sleepCat = c;
            } else if (lower.contains("habit") || lower.contains("micro")) {
                habitCat = c;
            }
        }

        // Generate recommendations for each pillar based on user's active Goal and Activity Level
        Optional<Recommendation> nutRec = nutritionCat != null
                ? recommendationEngine.generateBaseRecommendation(user, goal, nutritionCat.getId())
                : Optional.empty();

        Optional<Recommendation> exeRec = exerciseCat != null
                ? recommendationEngine.generateBaseRecommendation(user, goal, exerciseCat.getId())
                : Optional.empty();

        Optional<Recommendation> hydRec = hydrationCat != null
                ? recommendationEngine.generateBaseRecommendation(user, goal, hydrationCat.getId())
                : Optional.empty();

        Optional<Recommendation> slpRec = sleepCat != null
                ? recommendationEngine.generateBaseRecommendation(user, goal, sleepCat.getId())
                : Optional.empty();

        Optional<Recommendation> habRec = habitCat != null
                ? recommendationEngine.generateBaseRecommendation(user, goal, habitCat.getId())
                : Optional.empty();

        CalorieService.CalorieSummary calorieSummary = calorieService.calculateFor(user, goal);
        double hydrationLiters = hydrationService.suggestedLitersPerDay(user);
        boolean calorieRelevant = calorieService.isWeightRelevantGoal(goal);

        String goalCode = goal != null && goal.getCode() != null ? goal.getCode().toUpperCase(java.util.Locale.ROOT) : "";
        ActivityLevel actLevel = user != null && user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.SEDENTARY;

        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        // Fallback pillar actions if no specific row in DB
        String nutFallback;
        if (goalCode.contains("MUSCLE")) {
            if (age >= 50) {
                nutFallback = "Aim for 35–40g+ protein per meal to overcome age-related anabolic resistance and stimulate muscle protein synthesis; incorporate calcium, vitamin D, and omega-3s for bone and joint support.";
            } else if (age < 35) {
                nutFallback = "Include ~25–30g high-quality protein per meal paired with complex carbohydrates for muscle protein synthesis and training glycogen.";
            } else {
                nutFallback = "Include a protein-rich food source with each meal paired with complex carbohydrates for muscle synthesis.";
            }
        } else if (goalCode.contains("WEIGHT")) {
            if (age >= 50) {
                nutFallback = "Prioritize lean protein (25–30g+/meal) to protect lean muscle mass; incorporate abundant dietary fiber, calcium, and vitamin D.";
            } else {
                nutFallback = "Prioritize lean protein and abundant dietary fiber with balanced portions; minimize added sugars.";
            }
        } else if (goalCode.contains("SKIN")) {
            nutFallback = "Emphasize antioxidant-rich produce, healthy fats, and hydrating whole foods.";
        } else {
            nutFallback = "Structure balanced, wholesome meals with quality protein, vegetables, and complex carbohydrates.";
        }

        String exeFallback;
        if (goalCode.contains("MUSCLE")) {
            if (age >= 50) {
                exeFallback = "Prioritize joint-friendly resistance training in the 8–15 rep range with controlled 2–3s eccentrics; include 10–15 min dedicated mobility prep.";
            } else if (age < 35) {
                exeFallback = "Execute structured progressive resistance training (6–10 rep range) with weekly overload and standard warm-up.";
            } else {
                exeFallback = "Maintain 3–4 weekly training sessions balancing strength development and cardiovascular conditioning.";
            }
        } else {
            if (actLevel == ActivityLevel.SEDENTARY) {
                exeFallback = "Start with brisk 20–30 minute walks 4–5 times per week; add light bodyweight mobility routines.";
            } else if (actLevel == ActivityLevel.VERY_ACTIVE || actLevel == ActivityLevel.EXTRA_ACTIVE) {
                exeFallback = "Execute structured high-performance training with progressive overload and planned recovery days.";
            } else {
                exeFallback = "Maintain 3–4 weekly training sessions balancing strength development and cardiovascular conditioning.";
            }
        }

        String hydFallback;
        if (age >= 50) {
            hydFallback = String.format(java.util.Locale.ROOT, "Follow a proactive, clock-based hydration schedule (~%.1f L/day) to lubricate joints; taper fluids 2 hours before bed for undisturbed sleep.", hydrationLiters);
        } else if (age < 35) {
            hydFallback = String.format(java.util.Locale.ROOT, "Hydrate actively throughout the day and around training (~%.1f L/day) to sustain muscular fullness and cellular volumization.", hydrationLiters);
        } else {
            hydFallback = String.format(java.util.Locale.ROOT, "Sip water consistently throughout the day (target ~%.1f L/day) to support cellular health.", hydrationLiters);
        }

        String slpFallback;
        if (age >= 50) {
            slpFallback = "Aim for 7–8.5 hours of sleep nightly with 48–72h recovery between intense sessions; establish a relaxing 45–60 min evening wind-down.";
        } else if (age < 35) {
            slpFallback = "Aim for 7–9 hours of sleep nightly to capitalize on natural growth hormone recovery; allow 48 hours between training the same muscle group.";
        } else {
            slpFallback = "Aim for 7–9 hours of quality sleep nightly; maintain consistent sleep/wake times and dim lights before bed.";
        }

        String nutritionText = nutRec.map(this::extractPillarAction).orElse(nutFallback);
        String exerciseText = exeRec.map(this::extractPillarAction).orElse(exeFallback);
        String hydrationText = hydRec.map(this::extractPillarAction).orElse(hydFallback);
        String sleepText = slpRec.map(this::extractPillarAction).orElse(slpFallback);
        String habitText = habRec.isPresent()
                ? extractPillarAction(habRec.get())
                : buildDailyMicroHabitsActions(user, goal, actLevel);

        StringBuilder combinedActions = new StringBuilder();
        combinedActions.append("🍎 Nutrition:\n");
        combinedActions.append("   • ").append(nutritionText).append("\n\n");
        combinedActions.append("🏋 Exercise:\n");
        combinedActions.append("   • ").append(exerciseText).append("\n\n");
        combinedActions.append("💧 Hydration:\n");
        combinedActions.append("   • ").append(hydrationText).append("\n\n");
        combinedActions.append("😴 Sleep & Recovery:\n");
        combinedActions.append("   • ").append(sleepText).append("\n\n");
        combinedActions.append("🌱 Daily Micro-Habits:\n");
        combinedActions.append(formatMicroHabitsForMaster(habitText));

        String goalName = goal != null ? goal.getName() : "Daily Wellness";
        String actName = actLevel.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);

        String title = "Complete Master Routine (" + goalName + ")";
        String description = "A unified daily routine combining nutrition, exercise, hydration, sleep, and micro-habits "
                + "specifically calibrated for " + goalName + ", " + actName + " activity level, and age " + age + ".";

        StringBuilder targetSummary = new StringBuilder();
        if (calorieRelevant && calorieSummary != null) {
            targetSummary.append(String.format("Nutrition: ~%.0f kcal/day | ", calorieSummary.suggestedTarget));
        }
        targetSummary.append(String.format("Hydration: %.1f L/day | ", hydrationLiters));
        targetSummary.append("Exercise: 3–5 sessions/wk | Sleep: 7–9 hrs/night");

        String examples = "• Morning: 400–500 mL water + protein-rich breakfast\n"
                + "• Midday: Wholesome lunch + 20–30 min workout or brisk walk\n"
                + "• Evening: Balanced dinner + 30–60 min screen-free wind-down";

        String notes = "Consistency across all 5 pillars compounds over time. "
                + "Focus on executing small, sustainable daily actions rather than seeking perfection.";

        Recommendation masterRec;
        try {
            masterRec = recommendationDao.findOrCreateDynamicRecommendation(
                    goal != null ? goal.getId() : 1L,
                    masterCategory != null ? masterCategory.getId() : 5L,
                    actLevel,
                    title,
                    description,
                    combinedActions.toString(),
                    targetSummary.toString(),
                    examples,
                    notes
            );
        } catch (Exception e) {
            masterRec = new Recommendation(
                    -1L,
                    goal != null ? goal.getId() : null,
                    masterCategory != null ? masterCategory.getId() : 5L,
                    actLevel,
                    title,
                    description,
                    combinedActions.toString(),
                    targetSummary.toString(),
                    examples,
                    notes
            );
        }

        return new RecommendationResult(
                masterRec,
                calorieSummary,
                hydrationLiters,
                calorieRelevant
        );
    }

    private String extractPillarAction(Recommendation rec) {
        if (rec == null) return "";
        String act = rec.getRecommendedActions();
        if (act != null && !act.isBlank()) {
            return cleanSummaryLine(act);
        }
        String desc = rec.getDescription();
        if (desc != null && !desc.isBlank()) {
            return cleanSummaryLine(desc);
        }
        return cleanSummaryLine(rec.getTitle());
    }

    private String cleanSummaryLine(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\r?\\n+", " ");
    }

    public String buildDailyMicroHabitsActions(Goal goal, ActivityLevel actLevel) {
        return buildDailyMicroHabitsActions(null, goal, actLevel);
    }

    public String buildDailyMicroHabitsActions(User user, Goal goal, ActivityLevel actLevel) {
        String goalCode = goal != null && goal.getCode() != null
                ? goal.getCode().toUpperCase(java.util.Locale.ROOT)
                : "";
        ActivityLevel level = actLevel != null ? actLevel : ActivityLevel.SEDENTARY;
        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        if (goalCode.contains("MUSCLE")) {
            if (age >= 50) {
                return "• Complete a 5-minute morning mobility routine to lubricate joints and spine.\n"
                        + "• Keep a water bottle at your workspace and sip on a scheduled hourly basis.\n"
                        + "• Spend 5–10 minutes on gentle spinal decompression or stretching after training.\n"
                        + "• Dim overhead lights 60 minutes before bed and taper liquids for uninterrupted sleep.";
            } else if (age < 35) {
                return "• Prepare portable high-protein snacks or shakes ahead of time.\n"
                        + "• Stage workout gear and water bottle the night before for friction-free training.\n"
                        + "• Take a brisk 10-minute walk after your largest meal to support glucose disposal.\n"
                        + "• Set a digital screen curfew 45 minutes before sleep to protect sleep onset.";
            } else {
                return "• Pre-portion high-protein meals or snacks to avoid impulsive eating.\n"
                        + "• Take a 5-minute movement break every 90 minutes of desk work.\n"
                        + "• Hydrate with a full glass of water upon waking and before each meal.\n"
                        + "• Dim screens 45 minutes before bed to initiate sleep prep.";
            }
        } else if (goalCode.contains("WEIGHT")) {
            if (age >= 50) {
                return "• Complete a 5-minute morning joint mobility routine upon waking.\n"
                        + "• Drink a glass of water before each main meal and keep hourly hydration prompts.\n"
                        + "• Take a gentle 10-minute post-meal walk for blood glucose regulation.\n"
                        + "• Taper liquids and dim overhead lights 60 minutes before bedtime.";
            } else if (age < 35) {
                return "• Drink a glass of water before each main meal.\n"
                        + "• Prepare high-fiber, low-calorie snacks in advance.\n"
                        + "• Take a 10-minute brisk walk after lunch or dinner.\n"
                        + "• Set a screen-free alarm 45 minutes before bed.";
            } else {
                return "• Drink a glass of water before each main meal.\n"
                        + "• Prepare high-fiber, low-calorie snacks in advance.\n"
                        + "• Take a 10-minute walk after lunch or dinner.\n"
                        + "• Set a screen-free alarm 45 minutes before bed.";
            }
        } else if (goalCode.contains("SKIN")) {
            return "• Sip water consistently throughout the day.\n"
                    + "• Keep fresh fruit or whole food snacks easily accessible.\n"
                    + "• Cleanse face promptly after physical activity or workouts.\n"
                    + "• Start a relaxing evening wind-down at a consistent time.";
        } else if (goalCode.contains("SLEEP")) {
            return "• Dim overhead lights 60 minutes before your planned bedtime.\n"
                    + "• Keep phone and electronic screens away from the bed.\n"
                    + "• Sip warm caffeine-free tea or water during evening wind-down.\n"
                    + "• Maintain a consistent sleep schedule every day.";
        } else {
            return "• Drink a glass of water upon waking up.\n"
                    + "• Take short movement breaks during long sitting periods.\n"
                    + "• Prepare wholesome meals and snacks ahead of time.\n"
                    + "• Start evening decompression at a regular hour.";
        }
    }

    private String formatMicroHabitsForMaster(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append("    ").append(lines[i].trim());
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private Recommendation buildStandaloneHabitRecommendation(User user, Goal goal, RecommendationCategory category) {
        ActivityLevel actLevel = user != null && user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.SEDENTARY;
        String goalName = goal != null ? goal.getName() : "Daily Wellness";
        String actions = buildDailyMicroHabitsActions(user, goal, actLevel);
        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        String notes;
        if (age >= 50) {
            notes = "At age " + age + ", daily mobility and scheduled hydration habits safeguard joint health, spinal alignment, and restorative sleep.";
        } else if (age < 35) {
            notes = "At age " + age + ", low-friction habit triggers help maintain nutritional and training consistency around a dynamic schedule.";
        } else {
            notes = "These daily micro-habits are practical guidance to support your main recommendation, not a completion checklist.";
        }

        try {
            return recommendationDao.findOrCreateDynamicRecommendation(
                    goal != null ? goal.getId() : 1L,
                    category.getId(),
                    actLevel,
                    "Daily Micro-Habits (" + goalName + ")",
                    "Small, practical daily actions calibrated for " + goalName + ", your activity level, and age " + age + ".",
                    actions,
                    "3–4 daily micro-actions",
                    "Meal prep ahead of time, evening wind-down routine",
                    notes
            );
        } catch (Exception e) {
            return new Recommendation(
                    -1L,
                    goal != null ? goal.getId() : null,
                    category.getId(),
                    actLevel,
                    "Daily Micro-Habits (" + goalName + ")",
                    "Small, practical daily actions calibrated for " + goalName + ", your activity level, and age " + age + ".",
                    actions,
                    "3–4 daily micro-actions",
                    "Meal prep ahead of time, evening wind-down routine",
                    notes
            );
        }
    }

    private Recommendation buildStandaloneHydrationRecommendation(User user, Goal goal, RecommendationCategory category) {
        ActivityLevel actLevel = user != null && user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.SEDENTARY;
        String goalName = goal != null ? goal.getName() : "Daily Wellness";
        double hydrationLiters = hydrationService.suggestedLitersPerDay(user);
        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        String action;
        String notes;
        if (age >= 50) {
            action = String.format(java.util.Locale.ROOT, "Follow a proactive, clock-based hydration schedule (~%.1f L/day) rather than waiting for thirst (thirst cues naturally decline with age); drink steadily during daytime hours to lubricate joints and spinal discs, and taper fluids 2 hours before bed for undisturbed sleep.", hydrationLiters);
            notes = "At age " + age + ", thirst sensation is naturally blunted, so drink on a proactive schedule. Steady daytime hydration cushions joints and spinal discs, while evening tapering protects deep sleep.";
        } else if (age < 35) {
            action = String.format(java.util.Locale.ROOT, "Hydrate actively around workout sessions (~%.1f L/day, drinking 400–500 mL pre-workout and sipping during training); replace sweat losses promptly to sustain muscular strength, cell volumization, and workout stamina.", hydrationLiters);
            notes = "At age " + age + ", maintaining cellular hydration directly supports muscle fullness and workout power output. Drink readily to satisfy exercise-induced fluid losses.";
        } else {
            action = String.format(java.util.Locale.ROOT, "Sip water consistently throughout active hours (target ~%.1f L/day) to maintain cellular hydration, joint lubrication, and cognitive focus.", hydrationLiters);
            notes = "Distribute fluid intake evenly across the day and increase during periods of heat or elevated activity.";
        }

        String target = String.format(java.util.Locale.ROOT, "Approximately %.1f–%.1f L/day depending on activity", hydrationLiters, hydrationLiters + 0.5);

        try {
            return recommendationDao.findOrCreateDynamicRecommendation(
                    goal != null ? goal.getId() : 1L,
                    category.getId(),
                    actLevel,
                    "Hydration Guidance (" + goalName + ")",
                    "Targeted daily water intake guidance calibrated for " + goalName + ", your activity level, and age " + age + ".",
                    action,
                    target,
                    "• Morning: 500 mL upon waking\n• Daytime: 250–300 mL per active hour\n• Evening: Taper fluid intake before sleep",
                    notes
            );
        } catch (Exception e) {
            return new Recommendation(
                    -1L,
                    goal != null ? goal.getId() : null,
                    category.getId(),
                    actLevel,
                    "Hydration Guidance (" + goalName + ")",
                    "Targeted daily water intake guidance calibrated for " + goalName + ", your activity level, and age " + age + ".",
                    action,
                    target,
                    "• Morning: 500 mL upon waking\n• Daytime: 250–300 mL per active hour\n• Evening: Taper fluid intake before sleep",
                    notes
            );
        }
    }

    public Recommendation persistDynamicRecommendation(Recommendation rec) throws SQLException {
        if (rec == null) return null;
        if (rec.getId() != null && rec.getId() > 0) return rec;
        Recommendation persisted = recommendationDao.findOrCreateDynamicRecommendation(
                rec.getGoalId() != null ? rec.getGoalId() : 1L,
                rec.getCategoryId() != null ? rec.getCategoryId() : 5L,
                rec.getActivityLevel(),
                rec.getTitle(),
                rec.getDescription(),
                rec.getRecommendedActions(),
                rec.getSuggestedTarget(),
                rec.getExamples(),
                rec.getImportantNotes()
        );
        rec.setId(persisted.getId());
        return persisted;
    }
}
