package com.lifeforge.service;

import com.lifeforge.config.AppConfig;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Goal;
import com.lifeforge.model.PersonalizedPlanResult;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.RecommendationCategory;
import com.lifeforge.model.RecommendationPriority;
import com.lifeforge.model.User;
import com.lifeforge.util.HydrationCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiExplanationService implements RecommendationExplanationService {

    public static final String OFFLINE_NOTICE =
            "[AI Offline: Rule-based engine active. Check if Ollama is running on port 11434.]";

    private final List<Map<String, String>> conversationHistory = new ArrayList<>();

    public List<Map<String, String>> getConversationHistory() {
        return Collections.unmodifiableList(conversationHistory);
    }

    public void clearConversationHistory() {
        conversationHistory.clear();
    }

    private void pruneConversationHistory() {
        while (conversationHistory.size() > 9) {
            conversationHistory.remove(1);
        }
    }

    /**
     * Set to true only for local debugging. When false, the informational
     * [LifeForge AI] console lines are suppressed so they don't print
     * underneath the TUI. Actual error handling / fallback behavior is
     * unaffected either way - only the println calls are gated.
     */
    private static final boolean VERBOSE = false;

    private final RuleBasedExplanationService fallback =
            new RuleBasedExplanationService();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public boolean isAvailable() {
        return AppConfig.isAiEnabled();
    }

    @Override
    public ExplanationOutcome explain(
            User user,
            Goal goal,
            ActivityLevel activityLevel,
            Recommendation recommendation) {

        if (!isAvailable()) {
            if (VERBOSE) System.out.println("[LifeForge AI] AI is disabled.");
            return fallback.explain(user, goal, activityLevel, recommendation);
        }

        try {
            String prompt = buildPrompt(
                    user,
                    goal,
                    activityLevel,
                    recommendation
            );

            String response = callOllama(prompt);

            if (response == null || response.isBlank()) {
                if (VERBOSE) System.err.println(
                        "[LifeForge AI] Ollama returned an empty response."
                );

                return fallback.explain(
                        user,
                        goal,
                        activityLevel,
                        recommendation
                );
            }

            if (VERBOSE) System.out.println(
                    "[LifeForge AI] AI explanation generated successfully."
            );

            return new ExplanationOutcome(
                    response.trim(),
                    true
            );

        } catch (Exception e) {

            if (VERBOSE) System.err.println(
                    "[LifeForge AI] AI request failed: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
            );

            return fallback.explain(
                    user,
                    goal,
                    activityLevel,
                    recommendation
            );
        }
    }

    public static String buildSystemPrompt(User user, Goal currentGoal, RecommendationCategory currentCategory) {
        return buildSystemPrompt(user, currentGoal, currentCategory, null);
    }

    public static String buildSystemPrompt(User user, Goal currentGoal, RecommendationCategory currentCategory, Recommendation currentRecommendation) {
        String categoryName = (currentCategory != null) ? currentCategory.getName() : "General Health";

        int age = (user != null && user.getAge() != null) ? user.getAge() : 0;
        String gender = (user != null && user.getGender() != null) ? user.getGender().name() : "Not specified";
        double height = (user != null && user.getHeight() != null) ? user.getHeight() : 0.0;
        double weight = (user != null && user.getWeight() != null) ? user.getWeight() : 0.0;
        double bmi = (user != null && user.getBmi() != null) ? user.getBmi() : 0.0;
        String activityLevel = (user != null && user.getActivityLevel() != null)
                ? user.getActivityLevel().name().replace('_', ' ').toLowerCase(Locale.ROOT)
                : "Not specified";

        String goalContext;
        if (currentGoal != null) {
            StringBuilder gc = new StringBuilder();
            gc.append(String.format(Locale.ROOT, "Active Goal: '%s' | Active Category: '%s'. ", currentGoal.getName(), categoryName));
            if (currentRecommendation != null) {
                gc.append(String.format(Locale.ROOT, "Active Recommendation: '%s'. ",
                        currentRecommendation.getTitle() != null ? currentRecommendation.getTitle() : ""));
                if (currentRecommendation.getDescription() != null && !currentRecommendation.getDescription().isBlank()) {
                    gc.append(String.format(Locale.ROOT, "Recommendation Context: '%s'. ", currentRecommendation.getDescription().trim()));
                }
                if (currentRecommendation.getRecommendedActions() != null && !currentRecommendation.getRecommendedActions().isBlank()) {
                    gc.append(String.format(Locale.ROOT, "Recommended Actions: '%s'. ", currentRecommendation.getRecommendedActions().replaceAll("\\r?\\n+", " ").trim()));
                }
                if (currentRecommendation.getSuggestedTarget() != null && !currentRecommendation.getSuggestedTarget().isBlank()) {
                    gc.append(String.format(Locale.ROOT, "Suggested Target: '%s'. ", currentRecommendation.getSuggestedTarget().trim()));
                }
                if (currentRecommendation.getExamples() != null && !currentRecommendation.getExamples().isBlank()) {
                    gc.append(String.format(Locale.ROOT, "Examples: '%s'. ", currentRecommendation.getExamples().replaceAll("\\r?\\n+", " ").trim()));
                }
            }
            goalContext = gc.toString();
        } else {
            goalContext = String.format(Locale.ROOT,
                    "Goal Status: No goal selected yet | Active Category: '%s'. " +
                    "CRITICAL GOAL RULES: " +
                    "- The user has NOT selected a goal yet. " +
                    "- Do NOT repeat the boilerplate phrase 'Please choose a goal through the normal Choose Goal flow' in every message. " +
                    "- Do NOT assume, assign, mention, or display any active goal (such as 'General Wellness'). " +
                    "- Do not pretend or state that an active goal exists. " +
                    "- Recognize clearly that no goal has been selected yet. " +
                    "- You may use the user's real profile (age, gender, height, weight, activity level) to provide guidance when relevant. " +
                    "- If the user asks 'Which goal is matching with me?' or asks about goal choice: " +
                    "  1. List LIFEForge's exact 3 core goals directly: 1. Build Lean Muscle, 2. Lose Weight / Fat Loss, 3. General Health & Vitality. " +
                    "  2. Recommend the best goal based on their metrics (e.g., 'Given your moderate activity and normal BMI, 'Build Lean Muscle' or 'General Health & Vitality' fits you best.'). " +
                    "  3. Give clear navigation instructions: 'Press [ESC] to return to the Main Menu, then press [2] to lock in your goal.' ",
                    categoryName);
        }

        return String.format(Locale.ROOT,
                "You are the LIFEForge Health Assistant. " +
                "%s" +
                "User Metrics: Age %d, Gender: %s, Height %.1f cm, Weight %.1f kg (BMI %.1f), Activity Level: %s. " +
                "Rules: " +
                "1. Directly answer the user's specific query (e.g., duration, routine, dosage, frequency) rather than repeating boilerplate definitions. " +
                "2. Support all lifestyle categories (Exercise, Nutrition, Sleep, Hydration, Mental Wellness, Posture, etc.). " +
                "3. Clinical BMI reference: <18.5 is Underweight, 18.5-24.9 is Normal, 25.0-29.9 is Overweight, >=30.0 is Obese. " +
                "4. Keep responses grounded, actionable, and under 3 concise sentences unless explicitly asked for a list. " +
                "5. Directly answer the user's ACTUAL question instead of blindly following the current recommendation. If asked about a meal (e.g. 'What should I eat for breakfast?'), answer the breakfast question directly. If asked why this recommendation fits (e.g. 'Why should I eat more protein?'), explain the recommendation directly. " +
                "6. GOAL CONFLICT RECOGNITION: Detect when the user's question conflicts with the current goal (e.g. current goal is Lose Weight, but question is about gaining weight, bulking, or gaining 3kg in 1 week; or current goal is Gain Weight, but question is about losing weight). In any conflict: " +
                "   a. Recognize and clearly explain that their current active goal is '%s', while their question is about a different goal. " +
                "   b. Do NOT automatically change their selected goal. " +
                "   c. Do NOT pretend they are already using that other goal. " +
                "   d. Do NOT ignore the user's question. Provide safe, useful general guidance instead of an extreme rapid-weight change plan (explain that rapid changes like 3kg in 1 week are unrealistic and unsafe; healthy weight change is gradual, typically 0.25-0.5 kg/week). " +
                "   e. If the user wants personalized recommendations for another goal, explicitly instruct them to change their goal through the normal Choose Goal flow (press [ESC] to return to the Main Menu, then select Choose Goal). " +
                "   f. Do not invent new calorie, protein, hydration, weight, or exercise targets, and do not make unsupported medical claims.",
                goalContext, age, gender, height, weight, bmi, activityLevel, (currentGoal != null ? currentGoal.getName() : "None")
        );
    }

    /**
     * Answers an optional user question using the current LifeForge context.
     * It cannot select, replace, edit, or persist a recommendation. Failures
     * return a clean, local fallback so the TUI remains usable offline.
     */
    public AiChatResponse chat(User user, Goal goal, RecommendationCategory category,
                               Recommendation recommendation, List<String> recentConversation,
                               String question) {
        return chat(user, goal, category, recommendation, null, recentConversation, question);
    }

    public AiChatResponse chat(User user, Goal goal, RecommendationCategory category,
                               Recommendation recommendation, PersonalizedPlanResult plan,
                               List<String> recentConversation,
                               String question) {
        if (question == null || question.isBlank()) {
            return new AiChatResponse("Please enter a question for the AI assistant.", false);
        }
        Long catId = category != null ? category.getId() : null;
        String catName = category != null ? category.getName() : null;

        String systemPrompt = buildSystemPrompt(user, goal, category, recommendation);

        // a. Ensure the system role prompt sits at index 0.
        Map<String, String> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);

        if (conversationHistory.isEmpty()) {
            conversationHistory.add(sysMsg);
        } else {
            conversationHistory.set(0, sysMsg);
        }

        // b. Append {"role": "user", "content": userQuestion}.
        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", question.trim());
        conversationHistory.add(userMsg);
        pruneConversationHistory();

        if (!isAvailable()) {
            return chatFallbackWithNotice(user, goal, category, recommendation, plan, question);
        }
        try {
            // c. POST the full messages payload to http://localhost:11434/api/chat with "stream": false.
            String response = callOllamaChat(conversationHistory);
            if (response == null || response.isBlank()) {
                return chatFallbackWithNotice(user, goal, category, recommendation, plan, question);
            }
            if (!isKhmerRequest(question) && containsKhmer(response)) {
                if (VERBOSE) System.err.println("[LifeForge AI] Model leaked into Khmer on English question. Falling back to calibrated guidance.");
                return chatFallbackWithNotice(user, goal, category, recommendation, plan, question);
            }
            if (containsKhmer(response)) {
                response = sanitizeKhmerText(response);
            }

            if (checkGoalConflict(goal, question) != null) {
                String respLower = response.toLowerCase(Locale.ROOT);
                boolean mentionedGoal = (goal != null && goal.getName() != null && respLower.contains(goal.getName().toLowerCase(Locale.ROOT)))
                        || respLower.contains("current goal")
                        || respLower.contains("active goal");
                boolean addressedConflict = respLower.contains("different goal")
                        || respLower.contains("conflict")
                        || respLower.contains("choose goal")
                        || respLower.contains("gain")
                        || respLower.contains("lose")
                        || respLower.contains("gradual");
                if (!mentionedGoal && !addressedConflict) {
                    if (VERBOSE) System.err.println("[LifeForge AI] Model failed to address goal conflict. Using calibrated guidance.");
                    return chatFallbackWithNotice(user, goal, category, recommendation, plan, question);
                }
            }

            // d. Append Ollama's response: {"role": "assistant", "content": aiReply}.
            Map<String, String> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", response.trim());
            conversationHistory.add(assistantMsg);
            pruneConversationHistory();

            CategoryMatch match = detectCategoryMatch(plan, question, response);
            Long suggestedId = match != null ? match.id : catId;
            String suggestedName = match != null ? match.name : catName;
            return new AiChatResponse(response.trim(), true, suggestedId, suggestedName);
        } catch (Exception e) {
            if (VERBOSE) System.err.println("[LifeForge AI] Chat request failed: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return chatFallbackWithNotice(user, goal, category, recommendation, plan, question);
        }
    }

    private AiChatResponse chatFallbackWithNotice(User user, Goal goal, RecommendationCategory category,
                                                  Recommendation recommendation, PersonalizedPlanResult plan,
                                                  String question) {
        AiChatResponse base = chatFallback(user, goal, category, recommendation, plan, question);
        String textWithNotice = OFFLINE_NOTICE + "\n\n" + base.text;

        Map<String, String> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", textWithNotice);
        conversationHistory.add(assistantMsg);
        pruneConversationHistory();

        return new AiChatResponse(textWithNotice, false, base.suggestedCategoryId, base.suggestedCategoryName);
    }

    public static class GoalConflictInfo {
        public final String conflictingTopic;
        public final String safeGuidance;

        public GoalConflictInfo(String conflictingTopic, String safeGuidance) {
            this.conflictingTopic = conflictingTopic;
            this.safeGuidance = safeGuidance;
        }
    }

    public static GoalConflictInfo checkGoalConflict(Goal goal, String question) {
        if (goal == null || goal.getName() == null || question == null) {
            return null;
        }
        String gLower = goal.getName().toLowerCase(Locale.ROOT);
        String qLower = question.toLowerCase(Locale.ROOT);

        boolean isLossGoal = gLower.contains("lose weight") || gLower.contains("weight loss")
                || gLower.contains("fat loss") || gLower.contains("cut");
        boolean isGainGoal = gLower.contains("gain weight") || gLower.contains("build muscle")
                || gLower.contains("muscle") || gLower.contains("hypertrophy") || gLower.contains("bulk");

        // Check if user is asking to gain weight while on a weight loss goal
        boolean asksWeightGain = qLower.contains("gain weight") || qLower.contains("gaining weight")
                || qLower.contains("weight gain") || qLower.contains("bulk") || qLower.contains("bulking")
                || qLower.contains("gain muscle") || qLower.contains("build muscle")
                || qLower.matches(".*\\bgain\\s+\\d+.*")
                || (qLower.contains("gain") && (qLower.contains("kg") || qLower.contains("kilo") || qLower.contains("lbs") || qLower.contains("pound") || qLower.contains("mass") || qLower.contains("fat")));

        if (isLossGoal && asksWeightGain) {
            String guidance;
            if (qLower.contains("1 week") || qLower.contains("one week") || qLower.contains("week")
                    || qLower.matches(".*\\b\\d+\\s*(kg|kilo|lbs|pound).*")) {
                guidance = "Gaining 3 kg (or rapid weight) in one week is unrealistic and unsafe. " +
                        "Safe, sustainable weight adjustments occur gradually (typically 0.25–0.5 kg per week) " +
                        "through a modest caloric surplus, nutrient-dense whole foods, and progressive resistance training, " +
                        "rather than rapid weight spikes.";
            } else {
                guidance = "Healthy weight gain requires a gradual, controlled caloric surplus paired with " +
                        "progressive resistance training (targeting 0.25–0.5 kg per week) rather than rapid weight changes.";
            }
            return new GoalConflictInfo("gaining weight", guidance);
        }

        // Check if user is asking to lose weight while on a weight gain / muscle building goal
        boolean asksWeightLoss = qLower.contains("lose weight") || qLower.contains("losing weight")
                || qLower.contains("weight loss") || qLower.contains("fat loss") || qLower.contains("cut weight")
                || qLower.contains("cutting weight")
                || qLower.matches(".*\\blose\\s+\\d+.*")
                || (qLower.contains("lose") && (qLower.contains("kg") || qLower.contains("kilo") || qLower.contains("lbs") || qLower.contains("pound") || qLower.contains("fat")));

        if (isGainGoal && asksWeightLoss) {
            String guidance;
            if (qLower.contains("1 week") || qLower.contains("one week") || qLower.contains("week")
                    || qLower.matches(".*\\b\\d+\\s*(kg|kilo|lbs|pound).*")) {
                guidance = "Losing weight rapidly in one week is unrealistic and unsafe, often causing muscle loss and dehydration. " +
                        "Sustainable fat loss occurs gradually (typically around 0.5 kg per week) through a modest " +
                        "caloric deficit, adequate protein, and regular physical activity while preserving lean muscle.";
            } else {
                guidance = "Sustainable fat loss occurs gradually (typically around 0.5 kg per week) through a modest " +
                        "caloric deficit, adequate protein, and regular physical activity while preserving lean muscle.";
            }
            return new GoalConflictInfo("losing weight", guidance);
        }

        return null;
    }

    public AiChatResponse chatFallback(User user, Goal goal, RecommendationCategory category,
                                        Recommendation recommendation, String question) {
        return chatFallback(user, goal, category, recommendation, null, question);
    }

    public AiChatResponse chatFallback(User user, Goal goal, RecommendationCategory category,
                                        Recommendation recommendation, PersonalizedPlanResult plan,
                                        String question) {
        Long catId = category != null ? category.getId() : null;
        String catName = category != null ? category.getName() : null;
        String goalName = goal != null ? goal.getName() : "your active goal";
        String recTitle = recommendation != null && recommendation.getTitle() != null
                ? recommendation.getTitle()
                : "your current recommendation";

        StringBuilder sb = new StringBuilder();
        String qLower = question != null ? question.toLowerCase(Locale.ROOT).trim() : "";

        // Goal Conflict Detection
        GoalConflictInfo conflict = checkGoalConflict(goal, question);
        if (conflict != null) {
            sb.append("Your current active goal is '").append(goalName).append("', ")
              .append("while your question is about ").append(conflict.conflictingTopic).append(".\n\n")
              .append("• Safe Guidance:\n  ")
              .append(conflict.safeGuidance).append("\n\n")
              .append("• Goal Selection:\n  ")
              .append("Your current goal remains '").append(goalName).append("'. ")
              .append("If you would like personalized recommendations, targets, and meal guidance for ")
              .append(conflict.conflictingTopic).append(", please change your goal through the normal Choose Goal flow ")
              .append("(press [ESC] to return to the Main Menu, then select Choose Goal).");
            return new AiChatResponse(sb.toString().trim(), false, catId, catName);
        }

        // Check if user is asking for Khmer translation or Khmer guidance
        if (qLower.contains("khmer") || qLower.contains("ខ្មែរ")) {
            sb.append("ការណែនាំជាភាសាខ្មែរ សម្រាប់គោលដៅ ").append(goalName).append(":\n\n");
            if (goalName.toLowerCase(Locale.ROOT).contains("skin")) {
                sb.append("• ការទទួលទានជាតិទឹក: ផឹកទឹកឱ្យបាន ២.០ ដល់ ២.៥ លីត្រជារៀងរាល់ថ្ងៃ ដើម្បីរក្សាសំណើមស្បែក និងជំរុញការបណ្តេញជាតិពុល។\n");
                sb.append("• អាហារូបត្ថម្ភជំនួយស្បែក: ផ្តោតលើបន្លែស្រស់ ផ្លែឈើសម្បូរវីតាមីន C និង E ប្រូតេអ៊ីនគ្មានខ្លាញ់ និងខ្លាញ់ល្អ (Omega-3)។\n");
                sb.append("• ទម្លាប់ប្រចាំថ្ងៃ: លាងសម្អាតមុខថ្នមៗ គេងឱ្យបាន ៧-៩ ម៉ោងក្នុងមួយយប់ និងការពារស្បែកពីពន្លឺព្រះអាទិត្យ។\n");
            } else if (goalName.toLowerCase(Locale.ROOT).contains("muscle")) {
                sb.append("• ការទទួលទានប្រូតេអ៊ីន: ទទួលទានប្រូតេអ៊ីនឱ្យបានទៀងទាត់ក្នុងគ្រប់ពេលបាយ ដើម្បីជួសជុល និងកសាងសាច់ដុំ។\n");
                sb.append("• ការហាត់ប្រាណ: ផ្តោតលើការហាត់ទម្ងន់ ៣ ទៅ ៤ ដងក្នុងមួយសប្តាហ៍ ដើម្បីជំរុញការលូតលាស់សាច់ដុំ។\n");
                sb.append("• ការសម្រាក: គេងឱ្យបានគ្រប់គ្រាន់ ៧-៩ ម៉ោង ដើម្បីឱ្យរាងកាយស្តារកម្លាំង និងជាលិកាសាច់ដុំឡើងវិញ។\n");
            } else if (goalName.toLowerCase(Locale.ROOT).contains("weight") || goalName.toLowerCase(Locale.ROOT).contains("fat")) {
                sb.append("• របបអាហារ: ទទួលទានអាហារសម្បូរជាតិសរសៃ និងប្រូតេអ៊ីន ដើម្បីជួយឱ្យឆ្អែតបានយូរ និងគ្រប់គ្រងកាឡូរី។\n");
                sb.append("• ជាតិទឹក: ផឹកទឹកមុនពេលបាយ ដើម្បីជួយសម្រួលដល់ការរំលាយអាហារ និងកាត់បន្ថយការឃ្លាន។\n");
                sb.append("• សកម្មភាពរាងកាយ: បង្កើនការដើរ និងធ្វើចលនារាងកាយឱ្យបានទៀងទាត់ជារៀងរាល់ថ្ងៃ។\n");
            } else {
                sb.append("• ជាតិទឹក: ផឹកទឹកឱ្យបានគ្រប់គ្រាន់ជារៀងរាល់ថ្ងៃ ដើម្បីទ្រទ្រង់មុខងារកោសិកា។\n");
                sb.append("• អាហារូបត្ថម្ភ: ទទួលទានអាហារមានតុល្យភាព សម្បូរជីវជាតិ និងកាត់បន្ថយអាហារកែច្នៃ។\n");
                sb.append("• ដំណេក និងការសម្រាក: រក្សាពេលវេលាគេងឱ្យបានទៀងទាត់ ដើម្បីសុខភាពរាងកាយ និងផ្លូវចិត្តល្អ។\n");
            }
            return new AiChatResponse(sb.toString().trim(), false, catId, catName);
        }

        // Check if user is asking about hydration
        if (qLower.contains("water") || qLower.contains("hydration") || qLower.contains("drink") || qLower.contains("liters")) {
            PersonalizedPlanResult.AreaItem hydArea = findAreaByKeyword(plan, "hydration");
            double liters = (user != null && user.getWeightKg() != null)
                    ? HydrationCalculator.suggestedLitersPerDay(user.getWeightKg(), user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.MODERATELY_ACTIVE)
                    : 2.5;
            sb.append("Hydration Guidance for ").append(goalName).append(":\n\n");
            sb.append(String.format(Locale.ROOT, "• Suggested Daily Target: %.1f L/day of water.\n", liters));
            if (hydArea != null && hydArea.recommendation() != null) {
                if (hydArea.recommendation().getRecommendedActions() != null) {
                    sb.append("• Actions: ").append(hydArea.recommendation().getRecommendedActions().replaceAll("\\r?\\n+", " ")).append("\n");
                }
            }
            if (goalName.toLowerCase(Locale.ROOT).contains("skin")) {
                sb.append("• Skin Health Focus: Consistent cellular hydration supports skin elasticity, cell turnover, and the natural moisture barrier.\n");
                sb.append("• Tip: Keep a water bottle handy and distribute water intake evenly throughout the day.\n");
            } else {
                sb.append("• Tip: Consistent hydration supports cellular function, energy levels, and metabolic efficiency.\n");
            }
            Long resId = (hydArea != null && hydArea.category() != null) ? hydArea.category().getId() : catId;
            String resName = (hydArea != null && hydArea.category() != null) ? hydArea.category().getName() : catName;
            return new AiChatResponse(sb.toString().trim(), false, resId, resName);
        }

        // Check if user is asking about sleep or in sleep context
        boolean isSleepQuery = qLower.contains("sleep") || qLower.contains("rest") || qLower.contains("bedtime")
                || qLower.contains("recovery") || qLower.contains("slep")
                || ((category != null && category.getName() != null && category.getName().toLowerCase(Locale.ROOT).contains("sleep"))
                    && (qLower.contains("time") || qLower.contains("how many") || qLower.contains("hour") || qLower.contains("effective") || qLower.contains("good")));

        if (isSleepQuery) {
            PersonalizedPlanResult.AreaItem sleepArea = findAreaByKeyword(plan, "sleep");
            sb.append("Consistent sleep duration stabilizes your circadian rhythm and supports ").append(goalName).append(".\n\n");
            sb.append("- Target Duration: Aim for 7–9 hours of continuous sleep nightly rather than compensating with long sleep sessions.\n");
            if (goalName.toLowerCase(Locale.ROOT).contains("skin")) {
                sb.append("- Skin Repair: Deep sleep stimulates collagen production and cellular repair while reducing cortisol levels.\n");
            } else {
                sb.append("- Hormonal Balance: Predictable sleep schedules optimize nighttime growth hormone and immune function.\n");
            }
            sb.append("- Consistency: Keep identical bed and wake times every day to maximize restorative sleep quality.\n");
            Long resId = (sleepArea != null && sleepArea.category() != null) ? sleepArea.category().getId() : catId;
            String resName = (sleepArea != null && sleepArea.category() != null) ? sleepArea.category().getName() : catName;
            return new AiChatResponse(sb.toString().trim(), false, resId, resName);
        }

        // Check if user is asking about habits or daily routines
        if (qLower.contains("habit") || qLower.contains("routine") || qLower.contains("micro") || qLower.contains("apply")) {
            PersonalizedPlanResult.AreaItem habitArea = findAreaByKeyword(plan, "habit");
            sb.append("Daily Routine & Habits for ").append(goalName).append(":\n\n");
            if (goalName.toLowerCase(Locale.ROOT).contains("skin")) {
                sb.append("• Morning: Gentle cleanse, antioxidant-rich breakfast, hydration, and daily broad-spectrum SPF protection.\n");
                sb.append("• Daytime: Regular water intake, balanced nutrient-dense meals, and avoiding resting hands on face.\n");
                sb.append("• Evening: Cleanse away environmental pollutants, apply moisturizer, and get 7–9 hours of restorative sleep.\n");
            }
            if (habitArea != null && habitArea.recommendation() != null) {
                if (habitArea.recommendation().getRecommendedActions() != null) {
                    sb.append("• Official Recommendation: ").append(habitArea.recommendation().getRecommendedActions().replaceAll("\\r?\\n+", " ")).append("\n");
                }
            } else if (!goalName.toLowerCase(Locale.ROOT).contains("skin")) {
                sb.append("• Start with 1–2 small, consistent habits each day rather than drastic changes.\n");
                sb.append("• Anchor new habits to existing routines (e.g. drinking water immediately upon waking).\n");
            }
            if (recommendation != null && recommendation.getRecommendedActions() != null && !goalName.toLowerCase(Locale.ROOT).contains("skin")) {
                sb.append("• Current Area Action: ").append(recommendation.getRecommendedActions().replaceAll("\\r?\\n+", " ")).append("\n");
            }
            Long resId = (habitArea != null && habitArea.category() != null) ? habitArea.category().getId() : catId;
            String resName = (habitArea != null && habitArea.category() != null) ? habitArea.category().getName() : catName;
            return new AiChatResponse(sb.toString().trim(), false, resId, resName);
        }

        // Check if user is asking about exercise or fitness
        if (qLower.contains("exercise") || qLower.contains("workout") || qLower.contains("train") || qLower.contains("fitness") || qLower.contains("gym")) {
            PersonalizedPlanResult.AreaItem exArea = findAreaByKeyword(plan, "exercise");
            if (exArea == null) exArea = findAreaByKeyword(plan, "fitness");
            sb.append("Exercise Guidance for ").append(goalName).append(":\n\n");
            if (exArea != null && exArea.recommendation() != null) {
                sb.append("• Focus: ").append(exArea.recommendation().getTitle()).append("\n");
                if (exArea.recommendation().getRecommendedActions() != null) {
                    sb.append("• Actions: ").append(exArea.recommendation().getRecommendedActions().replaceAll("\\r?\\n+", " ")).append("\n");
                }
                if (exArea.recommendation().getSuggestedTarget() != null) {
                    sb.append("• Target: ").append(exArea.recommendation().getSuggestedTarget()).append("\n");
                }
            }
            if (goalName.toLowerCase(Locale.ROOT).contains("skin")) {
                sb.append("• Circulation: Moderate cardiovascular or strength activity enhances blood circulation, nourishing skin cells with oxygen and nutrients.\n");
                sb.append("• Tip: Wash face or shower promptly after workouts to prevent sweat and bacteria buildup.\n");
            } else {
                sb.append("• Aim for 150 minutes of moderate activity weekly, complemented by 2–3 strength sessions.\n");
            }
            Long resId = (exArea != null && exArea.category() != null) ? exArea.category().getId() : catId;
            String resName = (exArea != null && exArea.category() != null) ? exArea.category().getName() : catName;
            return new AiChatResponse(sb.toString().trim(), false, resId, resName);
        }

        // Food / Nutrition / Eat / Breakfast / Nutrient / General recommendation
        sb.append("AI assistant is offline. Here is guidance for your ").append(goalName)
                .append(" goal regarding ").append(recTitle);
        if (catName != null) {
            sb.append(" (").append(catName).append(")");
        }
        sb.append(":\n\n");

        if (recommendation != null) {
            if ((qLower.contains("why") || qLower.contains("benefit") || qLower.contains("how does") || qLower.contains("reason"))
                    && recommendation.getDescription() != null && !recommendation.getDescription().isBlank()) {
                sb.append("• Why This Recommendation Fits:\n  ")
                        .append(recommendation.getDescription()).append("\n\n");
            }
            if ((qLower.contains("breakfast") || qLower.contains("eat") || qLower.contains("meal") || qLower.contains("food")
                    || qLower.contains("nutrient") || qLower.contains("diet") || qLower.contains("nutrition"))
                    && recommendation.getExamples() != null && !recommendation.getExamples().isBlank()) {
                sb.append("• Suggested Foods & Examples:\n  ").append(recommendation.getExamples()).append("\n\n");
            }
            if (recommendation.getRecommendedActions() != null && !recommendation.getRecommendedActions().isBlank()) {
                sb.append("• Recommended Actions:\n  ").append(recommendation.getRecommendedActions().replaceAll("\\r?\\n+", " ")).append("\n\n");
            }
            if (recommendation.getSuggestedTarget() != null && !recommendation.getSuggestedTarget().isBlank()) {
                sb.append("• Suggested Target: ").append(recommendation.getSuggestedTarget()).append("\n\n");
            }
            if (recommendation.getExamples() != null && !recommendation.getExamples().isBlank()
                    && !sb.toString().contains(recommendation.getExamples())) {
                sb.append("• Examples: ").append(recommendation.getExamples()).append("\n\n");
            }
            if (recommendation.getImportantNotes() != null && !recommendation.getImportantNotes().isBlank()) {
                sb.append("• Important Notes: ").append(recommendation.getImportantNotes());
            }
        }
        return new AiChatResponse(sb.toString().trim(), false, catId, catName);
    }

    /**
     * Answers a user question in Global Dashboard Assistant mode (Mode A).
     * Grounded in the full personalized plan, goal, profile, and calculations.
     */
    public AiChatResponse chatGlobal(
            User user,
            Goal goal,
            PersonalizedPlanResult plan,
            CalorieService.CalorieSummary calorieSummary,
            double hydrationLiters,
            boolean calorieRelevant,
            List<String> recentConversation,
            String question) {

        if (question == null || question.isBlank()) {
            return new AiChatResponse("Please enter a question for the AI assistant.", false);
        }

        String systemPrompt = buildGlobalAssistantPrompt(
                user, goal, plan, calorieSummary, hydrationLiters, calorieRelevant, recentConversation, question);

        // a. Ensure the system role prompt sits at index 0.
        Map<String, String> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);

        if (conversationHistory.isEmpty()) {
            conversationHistory.add(sysMsg);
        } else {
            conversationHistory.set(0, sysMsg);
        }

        // b. Append {"role": "user", "content": userQuestion}.
        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", question.trim());
        conversationHistory.add(userMsg);
        pruneConversationHistory();

        if (!isAvailable()) {
            return globalFallbackWithNotice(user, goal, plan, calorieSummary, hydrationLiters, calorieRelevant, question);
        }
        try {
            // c. POST the full messages payload to http://localhost:11434/api/chat with "stream": false.
            String response = callOllamaChat(conversationHistory);
            if (response == null || response.isBlank()) {
                return globalFallbackWithNotice(user, goal, plan, calorieSummary, hydrationLiters, calorieRelevant, question);
            }
            if (!isKhmerRequest(question) && containsKhmer(response)) {
                if (VERBOSE) System.err.println("[LifeForge AI] Global model leaked into Khmer on English question. Falling back to calibrated guidance.");
                return globalFallbackWithNotice(user, goal, plan, calorieSummary, hydrationLiters, calorieRelevant, question);
            }
            if (containsKhmer(response)) {
                response = sanitizeKhmerText(response);
            }

            // d. Append Ollama's response: {"role": "assistant", "content": aiReply}.
            Map<String, String> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", response.trim());
            conversationHistory.add(assistantMsg);
            pruneConversationHistory();

            CategoryMatch match = detectCategoryMatch(plan, question, response);
            Long catId = match != null ? match.id : null;
            String catName = match != null ? match.name : null;
            return new AiChatResponse(response.trim(), true, catId, catName);
        } catch (Exception e) {
            if (VERBOSE) System.err.println("[LifeForge AI] Global chat request failed: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return globalFallbackWithNotice(user, goal, plan, calorieSummary, hydrationLiters, calorieRelevant, question);
        }
    }

    private AiChatResponse globalFallbackWithNotice(User user, Goal goal, PersonalizedPlanResult plan,
                                                    CalorieService.CalorieSummary calorieSummary,
                                                    double hydrationLiters, boolean calorieRelevant,
                                                    String question) {
        AiChatResponse base = globalFallback(user, goal, plan, calorieSummary, hydrationLiters, calorieRelevant, question);
        String textWithNotice = OFFLINE_NOTICE + "\n\n" + base.text;

        Map<String, String> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", textWithNotice);
        conversationHistory.add(assistantMsg);
        pruneConversationHistory();

        return new AiChatResponse(textWithNotice, false, base.suggestedCategoryId, base.suggestedCategoryName);
    }

    private record CategoryMatch(Long id, String name) {}

    private CategoryMatch detectCategoryMatch(PersonalizedPlanResult plan, String question, String response) {
        if (plan == null || plan.getAreas() == null) return null;
        String combined = (question + " " + response).toLowerCase(Locale.ROOT);

        PersonalizedPlanResult.AreaItem best = null;
        if (combined.contains("nutrition") || combined.contains("eat") || combined.contains("food")
                || combined.contains("diet") || combined.contains("meal") || combined.contains("protein")
                || combined.contains("breakfast") || combined.contains("lunch") || combined.contains("dinner") || combined.contains("snack")) {
            best = findAreaByKeyword(plan, "nutrition");
        } else if (combined.contains("exercise") || combined.contains("workout") || combined.contains("train")
                || combined.contains("fitness") || combined.contains("lifting") || combined.contains("walk")) {
            best = findAreaByKeyword(plan, "exercise");
        } else if (combined.contains("hydration") || combined.contains("water") || combined.contains("drink") || combined.contains("liters")) {
            best = findAreaByKeyword(plan, "hydration");
        } else if (combined.contains("sleep") || combined.contains("recovery") || combined.contains("rest") || combined.contains("bedtime")) {
            best = findAreaByKeyword(plan, "sleep");
        } else if (combined.contains("habit") || combined.contains("micro") || combined.contains("routine")) {
            best = findAreaByKeyword(plan, "habit");
        } else if (combined.contains("master")) {
            best = findAreaByKeyword(plan, "master");
        }

        if (best != null && best.category() != null) {
            return new CategoryMatch(best.category().getId(), best.category().getName());
        }
        return null;
    }

    private PersonalizedPlanResult.AreaItem findAreaByKeyword(PersonalizedPlanResult plan, String keyword) {
        if (plan == null || plan.getAreas() == null) return null;
        for (PersonalizedPlanResult.AreaItem a : plan.getAreas()) {
            if (a.category() != null && a.category().getName() != null
                    && a.category().getName().toLowerCase(Locale.ROOT).contains(keyword)) {
                return a;
            }
        }
        return null;
    }

    public AiChatResponse globalFallback(
            User user,
            Goal goal,
            PersonalizedPlanResult plan,
            CalorieService.CalorieSummary calorieSummary,
            double hydrationLiters,
            boolean calorieRelevant,
            String question) {

        String qLower = question != null ? question.toLowerCase(Locale.ROOT).trim() : "";
        StringBuilder sb = new StringBuilder();
        CategoryMatch match = detectCategoryMatch(plan, question, "");

        boolean hasGoal = goal != null;

        // 1. BMR / TDEE Questions
        if (qLower.contains("bmr") || qLower.contains("tdee")) {
            if (qLower.contains("my bmr") || qLower.contains("my tdee") || qLower.contains("what is my")) {
                CalorieService.CalorieSummary summary = calorieSummary;
                if (summary == null && user != null && user.getGender() != null && user.getWeightKg() != null
                        && user.getHeightCm() != null && user.getAge() != null && user.getActivityLevel() != null) {
                    try {
                        summary = new CalorieService().calculateFor(user, goal);
                    } catch (Exception ignored) {
                    }
                }
                if (summary != null && summary.bmr > 0) {
                    sb.append(String.format(Locale.ROOT,
                            "Based on your profile, your estimated Basal Metabolic Rate (BMR) is ~%.0f kcal/day, and your estimated Total Daily Energy Expenditure (TDEE) is ~%.0f kcal/day.\n\n" +
                            "BMR is what your body burns at complete rest, while TDEE reflects your total daily energy burn including your activity level.",
                            summary.bmr, summary.tdee));
                } else {
                    sb.append("Basal Metabolic Rate (BMR) is the number of calories your body burns at rest. Update your profile with your age, gender, height, weight, and activity level to calculate your personalized BMR in LIFEForge.");
                }
            } else if (qLower.contains("bmr")) {
                sb.append("BMR (Basal Metabolic Rate) is the number of calories your body needs to perform basic life-sustaining functions (such as breathing, circulation, and cell production) while at complete rest.");
            } else {
                sb.append("TDEE (Total Daily Energy Expenditure) is the total number of calories you burn each day. It combines your Basal Metabolic Rate (BMR) with the energy expended through daily movement, exercise, and digestion.");
            }
        // 2. Goal Comparison: Gain Weight vs Build Muscle
        } else if ((qLower.contains("difference") || qLower.contains("compare") || qLower.contains("vs"))
                && (qLower.contains("gain weight") || qLower.contains("build muscle"))) {
            sb.append("Here is the difference between Gain Weight and Build Muscle in LIFEForge:\n\n" +
                    "• Gain Weight: Focuses primarily on increasing overall body mass safely through a consistent nutritional caloric surplus, nutrient-dense meals, and healthy lifestyle habits.\n\n" +
                    "• Build Muscle: Focuses specifically on muscular hypertrophy and strength development through progressive resistance training, adequate protein distribution, and focused recovery.");
        // 3. Available Goals List
        } else if ((qLower.contains("goal") || qLower.contains("goals"))
                && (qLower.contains("available") || qLower.contains("exist") || qLower.contains("list") || qLower.contains("in lifeforge") || qLower.contains("all goal") || qLower.contains("official") || qLower.contains("what are"))) {
            sb.append("LIFEForge offers 8 primary goal areas:\n\n" +
                    "1. Lose Weight — Sustainable fat loss and caloric management\n" +
                    "2. Gain Weight — Healthy, gradual mass gain via nutritional surplus\n" +
                    "3. Build Muscle — Strength training and hypertrophy\n" +
                    "4. Improve Fitness — Cardiovascular endurance and daily stamina\n" +
                    "5. Improve Skin Health — Cellular hydration and antioxidant nutrition\n" +
                    "6. Improve Sleep — Circadian consistency and restorative rest\n" +
                    "7. General Wellness — Balanced, foundational health habits\n" +
                    "8. Posture Correction — Ergonomics, mobility, and core stability");
        // 4. Why specific goal fits (e.g. Gain Weight)
        } else if ((qLower.contains("why") || qLower.contains("how")) && qLower.contains("gain weight") && (qLower.contains("fit") || qLower.contains("profile") || qLower.contains("suit"))) {
            double height = (user != null && user.getHeightCm() != null) ? user.getHeightCm() : 0;
            double weight = (user != null && user.getWeightKg() != null) ? user.getWeightKg() : 0;
            double bmi = (height > 0 && weight > 0) ? weight / ((height / 100.0) * (height / 100.0)) : 0;
            sb.append("Gain Weight is designed for individuals looking to build body mass safely and sustainably.\n\n");
            if (bmi > 0 && bmi < 18.5) {
                sb.append(String.format(Locale.ROOT, "Given your leaner baseline (BMI %.1f), this goal pairs a caloric surplus with nutrient-dense foods and consistent meal timing to support healthy weight gain.\n", bmi));
            } else {
                sb.append("It establishes a structured nutritional foundation with an energy surplus and balanced macronutrients to support gradual mass increase without unnecessary metabolic stress.\n");
            }
        // 5. Why current goal fits (selected goal)
        } else if ((qLower.contains("why") || qLower.contains("how"))
                && (qLower.contains("my goal") || qLower.contains("current goal") || qLower.contains("selected goal"))
                && (qLower.contains("fit") || qLower.contains("suit") || qLower.contains("suitable"))) {
            if (hasGoal) {
                sb.append(String.format(Locale.ROOT,
                        "Your current goal ('%s') aligns with your profile and activity level by establishing targeted lifestyle recommendations and daily routines designed specifically for this objective.",
                        goal.getName()));
            } else {
                sb.append("You have not selected a goal yet. Based on your profile information, I can help you understand which LIFEForge goal areas may be relevant to explore.");
            }
        // 6. User asks about their profile
        } else if (qLower.contains("profile") && (qLower.contains("explain") || qLower.contains("tell me") || qLower.contains("about") || qLower.contains("what is my") || qLower.contains("summary"))) {
            double height = (user != null && user.getHeightCm() != null) ? user.getHeightCm() : 0;
            double weight = (user != null && user.getWeightKg() != null) ? user.getWeightKg() : 0;
            double bmi = (height > 0 && weight > 0) ? weight / ((height / 100.0) * (height / 100.0)) : 0;
            String bmiCat = bmi < 18.5 ? "Underweight" : bmi < 25.0 ? "Normal" : bmi < 30.0 ? "Overweight" : "Obese";
            String actStr = (user != null && user.getActivityLevel() != null)
                    ? user.getActivityLevel().name().replace('_', ' ').toLowerCase(Locale.ROOT)
                    : "Not specified";

            sb.append("Here is a summary of your profile information:\n\n");
            sb.append("• Age: ").append(user != null && user.getAge() != null ? user.getAge() : "Not specified").append('\n');
            sb.append("• Gender: ").append(user != null && user.getGender() != null ? user.getGender() : "Not specified").append('\n');
            sb.append(String.format(Locale.ROOT, "• Height: %.1f cm\n", height));
            sb.append(String.format(Locale.ROOT, "• Weight: %.1f kg\n", weight));
            if (bmi > 0) {
                sb.append(String.format(Locale.ROOT, "• Calculated BMI: %.1f (%s)\n", bmi, bmiCat));
            }
            sb.append("• Activity Level: ").append(actStr).append("\n\n");
            sb.append("This profile data is used as context to calibrate hydration, energy expenditure, and lifestyle recommendations.");
        // 7. Fatigue / Tired inquiry (Non-diagnostic)
        } else if (qLower.contains("tired") || qLower.contains("fatigue") || qLower.contains("exhausted") || qLower.contains("no energy") || qLower.contains("low energy")) {
            sb.append("Feeling tired can be influenced by multiple everyday lifestyle factors:\n\n" +
                    "• Sleep Quality & Duration: Inconsistent sleep schedules or getting fewer than 7-9 hours of restorative sleep.\n" +
                    "• Hydration: Mild dehydration often manifests as fatigue, sluggishness, or reduced alertness.\n" +
                    "• Nutrition & Fuel: Skipping balanced meals, insufficient protein, or large blood sugar swings from refined sugars.\n" +
                    "• Physical Movement & Stress: Prolonged sedentary periods or high sustained stress levels.\n\n" +
                    "Note: If fatigue is persistent, severe, or concerning, please discuss it with a qualified healthcare professional.");
        // 8. Sleep hygiene
        } else if (qLower.contains("sleep hygiene")) {
            sb.append("Sleep hygiene refers to daily habits and an optimal bedroom environment that support consistent, high-quality sleep:\n\n" +
                    "• Consistency: Keep identical bed and wake times every day, even on weekends.\n" +
                    "• Environment: Maintain a cool, quiet, and dark sleeping space.\n" +
                    "• Screen Curfew: Limit blue light exposure from phones, tablets, and computers 30-60 minutes before bedtime.\n" +
                    "• Wind-Down: Establish a calming pre-bed routine such as reading or light stretching.");
        // 9. Breakfast guidance
        } else if (qLower.contains("breakfast")) {
            sb.append("Here are practical, balanced breakfast ideas to support steady energy:\n\n" +
                    "• Protein & Greens: Eggs or scrambled tofu paired with sautéed spinach, mushrooms, and a slice of whole-grain toast.\n" +
                    "• High-Fiber Oats: Warm oatmeal topped with fresh berries, a handful of walnuts or almonds, and chia seeds.\n" +
                    "• Yogurt Bowl: Greek yogurt layered with sliced fruit, pumpkin seeds, and a light drizzle of honey.\n" +
                    "• Quick Smoothie: Blended unsweetened milk or water, a scoop of protein powder, a handful of greens, and half a banana.");
        // 10. Ambiguous improvement clarification
        } else if (qLower.equals("what should i improve?") || qLower.equals("what should i improve") || qLower.equals("how to improve?")) {
            sb.append("I can help with that. Would you like to focus on your nutrition, exercise, sleep, hydration, or choosing a LIFEForge goal?");
        // 11. Lifestyle improvement
        } else if (qLower.contains("lifestyle") || qLower.contains("improve my lifestyle")
                || (qLower.contains("improve my") && !qLower.contains("sleep") && !qLower.contains("food") && !qLower.contains("eat") && !qLower.contains("nutrition") && !qLower.contains("exercise") && !qLower.contains("workout") && !qLower.contains("water") && !qLower.contains("posture") && !qLower.contains("skin"))) {
            sb.append("To improve your daily lifestyle, LIFEForge focuses on core foundational pillars:\n\n" +
                    "• Hydration: Sip water steadily throughout the day.\n" +
                    "• Balanced Nutrition: Eat whole, nutrient-dense foods with protein at every meal.\n" +
                    "• Physical Movement: Engage in regular exercise matching your activity level.\n" +
                    "• Restorative Sleep: Maintain 7-9 hours of consistent sleep nightly.\n\n" +
                    "You can explore any of these areas or select a LIFEForge goal when you are ready.");
        // 12. Matching goal inquiry
        } else if (!hasGoal && qLower.contains("match")) {
            sb.append("You have not selected a goal yet. Here are LIFEForge's 3 core goals:\n\n");
            sb.append("1. Build Lean Muscle\n");
            sb.append("2. Lose Weight / Fat Loss\n");
            sb.append("3. General Health & Vitality\n\n");

            double height = (user != null && user.getHeightCm() != null) ? user.getHeightCm() : 0;
            double weight = (user != null && user.getWeightKg() != null) ? user.getWeightKg() : 0;
            double bmi = (height > 0 && weight > 0) ? weight / ((height / 100.0) * (height / 100.0)) : 0;
            String actStr = (user != null && user.getActivityLevel() != null)
                    ? user.getActivityLevel().name().replace('_', ' ').toLowerCase(Locale.ROOT)
                    : "moderate";

            String recommendation;
            if (bmi > 0 && bmi < 18.5) {
                recommendation = String.format(Locale.ROOT, "Given your %s activity and lower BMI (%.1f), 'Build Lean Muscle' fits you best.", actStr, bmi);
            } else if (bmi >= 25.0) {
                recommendation = String.format(Locale.ROOT, "Given your %s activity and BMI (%.1f), 'Lose Weight / Fat Loss' fits you best.", actStr, bmi);
            } else if (bmi > 0) {
                recommendation = String.format(Locale.ROOT, "Given your %s activity and normal BMI (%.1f), 'Build Lean Muscle' or 'General Health & Vitality' fits you best.", actStr, bmi);
            } else {
                recommendation = String.format(Locale.ROOT, "Given your %s activity level, 'General Health & Vitality' fits you best.", actStr);
            }
            sb.append(recommendation).append("\n\n");
            sb.append("Press [ESC] to return to the Main Menu, then press [2] to lock in your goal.");
        // 13. General goal inquiry
        } else if (qLower.contains("goal") || qLower.contains("suit") || qLower.contains("choose a goal")) {
            sb.append("Based on your profile, here is guidance on lifestyle directions that may suit you:\n\n");
            double height = (user != null && user.getHeightCm() != null) ? user.getHeightCm() : 0;
            double weight = (user != null && user.getWeightKg() != null) ? user.getWeightKg() : 0;
            double bmi = (height > 0 && weight > 0) ? weight / ((height / 100.0) * (height / 100.0)) : 0;

            if (bmi > 0 && bmi < 18.5) {
                sb.append(String.format(Locale.ROOT, "• Lean Profile (BMI %.1f): Directions such as 'Build Muscle' or 'Gain Weight' focus on progressive strength training and surplus nutrition.\n", bmi));
            } else if (bmi >= 25.0) {
                sb.append(String.format(Locale.ROOT, "• Profile Context (BMI %.1f): Directions such as 'Lose Weight' or 'Improve Fitness' pair caloric management with steady cardiovascular and strength activity.\n", bmi));
            } else if (bmi > 0) {
                sb.append(String.format(Locale.ROOT, "• Balanced Profile (BMI %.1f): Directions such as 'Build Muscle', 'Improve Fitness', or 'General Wellness' align well with your baseline.\n", bmi));
            } else {
                sb.append("• Lifestyle paths such as 'Improve Fitness' or 'General Wellness' align well with foundational health habits.\n");
            }
            sb.append("• Lifestyle & Recovery: If your focus is quality of life, 'Improve Sleep' or 'Skin Health' provide powerful habit foundations.\n\n");
            sb.append("Note: LIFEForge does not automatically select or change your goal.");
        // 14. Focus areas
        } else if (qLower.contains("focus") || qLower.startsWith("1")) {
            sb.append("Here are your key lifestyle focus areas:\n\n");
            if (hasGoal && plan != null && plan.getAreas() != null && !plan.getAreas().isEmpty()) {
                for (PersonalizedPlanResult.AreaItem area : plan.getAreas()) {
                    if (area.priority() == RecommendationPriority.HIGH || area.priority() == RecommendationPriority.RECOMMENDED) {
                        sb.append(area.emoji()).append(" ").append(area.category().getName())
                                .append(" (").append(area.priority().getLabel()).append(")\n");
                        if (area.recommendation() != null) {
                            sb.append("   • ").append(area.recommendation().getTitle()).append("\n");
                        }
                    }
                }
                sb.append(String.format("\n💧 Hydration Target: %.1f L/day\n", hydrationLiters));
                if (calorieRelevant && calorieSummary != null && calorieSummary.suggestedTarget > 0) {
                    sb.append(String.format("🎯 Calorie Target: ~%.0f kcal/day\n", calorieSummary.suggestedTarget));
                }
                if (match == null && !plan.getAreas().isEmpty()) {
                    PersonalizedPlanResult.AreaItem first = plan.getAreas().get(0);
                    match = new CategoryMatch(first.category().getId(), first.category().getName());
                }
            } else {
                sb.append(String.format("💧 Hydration — Target: %.1f L/day (steady water intake)\n", hydrationLiters));
                sb.append("🍎 Nutrition — Whole, nutrient-dense foods with protein at every meal\n");
                String actStr = user != null && user.getActivityLevel() != null
                        ? user.getActivityLevel().name().replace('_', ' ').toLowerCase(Locale.ROOT)
                        : "current";
                sb.append("🏃 Physical Activity — Regular movement appropriate for your ").append(actStr).append(" activity level\n");
                sb.append("😴 Rest & Recovery — 7-9 hours of consistent, restorative sleep");
            }
        // 15. Nutrition
        } else if (qLower.contains("eat") || qLower.contains("food") || qLower.contains("nutrition") || qLower.startsWith("2")) {
            PersonalizedPlanResult.AreaItem nut = findAreaByKeyword(plan, "nutrition");
            sb.append("Here is nutrition guidance tailored to your profile:\n\n");
            if (nut != null && nut.recommendation() != null) {
                sb.append("Official Recommendation: ").append(nut.recommendation().getTitle()).append("\n\n");
                if (nut.recommendation().getRecommendedActions() != null) {
                    sb.append("Guidance: ").append(nut.recommendation().getRecommendedActions().replaceAll("\\r?\\n+", " ")).append("\n\n");
                }
            } else {
                sb.append("Guidance: Include a protein-rich food source with each meal paired with abundant vegetables and complex carbohydrates.\n\n");
            }
            if (calorieRelevant && calorieSummary != null && calorieSummary.suggestedTarget > 0) {
                sb.append(String.format("Calorie Guideline: ~%.0f kcal/day\n", calorieSummary.suggestedTarget));
            } else if (calorieSummary != null && calorieSummary.tdee > 0) {
                sb.append(String.format("Maintenance Estimate: ~%.0f kcal/day\n", calorieSummary.tdee));
            }
            match = nut != null ? new CategoryMatch(nut.category().getId(), nut.category().getName()) : null;
        // 16. Exercise
        } else if (qLower.contains("exercise") || qLower.contains("workout") || qLower.startsWith("3")) {
            PersonalizedPlanResult.AreaItem exe = findAreaByKeyword(plan, "exercise");
            String actStr = user != null && user.getActivityLevel() != null
                    ? user.getActivityLevel().name().replace('_', ' ').toLowerCase(Locale.ROOT)
                    : "current";
            sb.append("Based on your ").append(actStr).append(" activity level, here is exercise guidance:\n\n");
            if (exe != null && exe.recommendation() != null) {
                sb.append("Official Recommendation: ").append(exe.recommendation().getTitle()).append("\n\n");
                if (exe.recommendation().getRecommendedActions() != null) {
                    sb.append("Actions: ").append(exe.recommendation().getRecommendedActions().replaceAll("\\r?\\n+", " ")).append("\n\n");
                }
            } else {
                sb.append("Actions: Maintain structured physical training sessions balancing strength development and cardiovascular conditioning.\n\n");
            }
            match = exe != null ? new CategoryMatch(exe.category().getId(), exe.category().getName()) : null;
        // 17. Sleep
        } else if (qLower.contains("sleep") || qLower.contains("rest") || qLower.contains("bedtime") || qLower.contains("recover") || qLower.startsWith("4")) {
            PersonalizedPlanResult.AreaItem slp = findAreaByKeyword(plan, "sleep");
            sb.append("Here is sleep and recovery guidance tailored to your profile:\n\n");
            if (slp != null && slp.recommendation() != null) {
                sb.append("Official Recommendation: ").append(slp.recommendation().getTitle()).append("\n\n");
                if (slp.recommendation().getRecommendedActions() != null) {
                    sb.append("Guidance: ").append(slp.recommendation().getRecommendedActions().replaceAll("\\r?\\n+", " ")).append("\n\n");
                }
            } else {
                sb.append("Guidance: Aim for 7-9 hours of consistent, restorative sleep each night. Establish a calming wind-down routine and limit blue light exposure 30-60 minutes before bedtime.\n\n");
            }
            sb.append("Key Target: 7-9 hours/night in a cool, quiet, and dark sleep environment.");
            match = slp != null ? new CategoryMatch(slp.category().getId(), slp.category().getName()) : null;
        // 18. Hydration
        } else if (qLower.contains("water") || qLower.contains("drink") || qLower.contains("hydration") || qLower.startsWith("5")) {
            String actStr = user != null && user.getActivityLevel() != null
                    ? user.getActivityLevel().name().replace('_', ' ').toLowerCase(Locale.ROOT)
                    : "current";
            double weight = user != null && user.getWeightKg() != null ? user.getWeightKg() : 0;
            sb.append(String.format("Based on your profile (weight %.1f kg, %s activity), LIFEForge calculates your daily hydration target as %.1f L/day.\n\n",
                    weight, actStr, hydrationLiters));
            sb.append("Guidance: Sip water steadily throughout the day rather than large amounts at once to support cellular metabolism and recovery.");
            PersonalizedPlanResult.AreaItem hyd = findAreaByKeyword(plan, "hydration");
            match = hyd != null ? new CategoryMatch(hyd.category().getId(), hyd.category().getName()) : null;
        // 19. Plan Overview
        } else if (hasGoal && (qLower.contains("plan") || qLower.contains("explain"))) {
            sb.append("Here is an overview of your LIFEForge personalized lifestyle plan:\n\n");
            if (plan != null && plan.getAreas() != null) {
                for (PersonalizedPlanResult.AreaItem a : plan.getAreas()) {
                    sb.append("• ").append(a.emoji()).append(" ").append(a.category().getName())
                            .append(" [").append(a.priority().getLabel()).append("]\n");
                }
            }
            sb.append(String.format("\nKey Targets: Hydration %.1f L/day", hydrationLiters));
            if (calorieRelevant && calorieSummary != null && calorieSummary.suggestedTarget > 0) {
                sb.append(String.format(" | Calorie Target ~%.0f kcal/day", calorieSummary.suggestedTarget));
            }
            sb.append(" | Sleep 7-9 hrs/night.");
        // 20. Default / General Lifestyle
        } else {
            sb.append("AI assistant is currently offline. Here is your official LIFEForge lifestyle guidance:\n\n");
            if (hasGoal && plan != null && plan.getAreas() != null) {
                for (PersonalizedPlanResult.AreaItem a : plan.getAreas()) {
                    if (a.priority() == RecommendationPriority.HIGH) {
                        sb.append("• ").append(a.emoji()).append(" ").append(a.category().getName())
                                .append(": ").append(a.recommendation() != null ? a.recommendation().getTitle() : "Guidance available").append("\n");
                    }
                }
            } else {
                sb.append("• Nutrition: Whole foods with protein and vegetables at each meal\n");
                sb.append("• Movement: Daily physical activity and 7-9 hours restorative sleep\n");
            }
            sb.append(String.format("\nDaily Hydration Target: %.1f L/day\n", hydrationLiters));
            if (calorieRelevant && calorieSummary != null && calorieSummary.suggestedTarget > 0) {
                sb.append(String.format("Calorie Target: ~%.0f kcal/day\n", calorieSummary.suggestedTarget));
            } else if (calorieSummary != null && calorieSummary.tdee > 0) {
                sb.append(String.format("Maintenance Calories: ~%.0f kcal/day\n", calorieSummary.tdee));
            }
        }

        Long catId = match != null ? match.id : null;
        String catName = match != null ? match.name : null;
        return new AiChatResponse(sb.toString().trim(), false, catId, catName);
    }

    public String buildGlobalAssistantPrompt(
            User user,
            Goal goal,
            PersonalizedPlanResult plan,
            CalorieService.CalorieSummary calorieSummary,
            double hydrationLiters,
            boolean calorieRelevant,
            List<String> recentConversation,
            String question) {

        StringBuilder sb = new StringBuilder("""
                You are the LIFEForge conversational assistant, a smart GENERAL LIFESTYLE ASSISTANT.
                Your primary job is to understand the user's question and intent FIRST, then answer that question directly.

                CRITICAL DIRECTIVES — UNDERSTAND INTENT FIRST:
                - Before answering, determine what the user is actually asking about.
                - Do NOT automatically convert every question into a weight range, calorie recommendation, goal recommendation, or medical explanation.
                - Only discuss those topics when they are directly relevant to the user's question.
                - Match your response to the user's actual question:
                  * If the user asks about goals (e.g. "Which goal fits me?", "I want to know about the goal that fit with me", "What goal should I choose?"):
                    Discuss suitable LIFEForge goal areas based on their profile. Do NOT invent a weight target (e.g. never say "I recommend 50-70 kg").
                  * If the user asks why a specific goal fits (e.g. "Why would Gain Weight fit my profile?"):
                    Explain how that goal relates to their profile. Do not invent unrelated weight targets.
                  * If the user asks about breakfast or food: give practical nutrition guidance. Do not force goal selection or weight analysis.
                  * If the user asks about exercise: give exercise guidance. Do not switch to nutrition unless relevant.
                  * If the user asks about sleep: give sleep guidance.
                  * If the user asks about sleep hygiene: explain sleep hygiene principles clearly.
                  * If the user asks about water: give hydration guidance quoting official LIFEForge targets if available.
                  * If the user asks "Why am I tired?": do NOT diagnose. Give general lifestyle factors (sleep, hydration, nutrition, stress) and note that persistent symptoms should be evaluated by a healthcare professional.
                  * If the user asks "What is BMR?" or "What is TDEE?": explain the concept clearly.
                  * If the user asks "What is my BMR?": quote the calculated BMR provided below if available.
                  * If the user asks "Can you explain my profile?" or "Tell me about my profile": summarize their profile information.
                  * If the user asks "What should I focus on?": analyze their profile and explain relevant focus areas without forcing a goal.
                  * If the user asks "What goals are available in LIFEForge?": list and explain the available goals.
                  * If the user asks "What is the difference between Gain Weight and Build Muscle?": compare those two goals clearly.
                  * If the user asks "Why is my current goal suitable for me?": use their selected goal and profile to explain the relationship.
                  * If the user's question is ambiguous (e.g. "What should I improve?"): ask a short clarification (e.g. nutrition, exercise, sleep, hydration, or choosing a goal) instead of guessing.

                LIFEFORGE GOALS:
                - Lose Weight
                - Gain Weight
                - Build Muscle
                - Improve Fitness
                - Improve Skin Health
                - Improve Sleep
                - General Wellness
                - Posture Correction

                GOAL CONTEXT & SELECTION RULES:
                - If the user has NOT selected a goal:
                  * Never assume any goal (never say "Your goal is General Wellness", etc.).
                  * Do NOT create or assign a goal automatically.
                  * Discuss relevant LIFEForge goals, explain why they may fit, and let the user make the final selection.
                  * Do NOT repeat boilerplate phrases like "Please choose a goal through the normal Choose Goal flow" in every message.
                - If the user ALREADY has a selected goal:
                  * Dashboard AI Assistant is still GENERAL / FREE.
                  * Do NOT display "Current Goal: ..." or "Your active goal is..." unless the user specifically asks about their current goal.
                  * Use the selected goal internally as context to make answers relevant, but answer the question directly.

                PROFILE-AWARE, NOT PROFILE-OBSESSED:
                - Use the profile as context when relevant. Do NOT force every answer into weight/calorie/BMI analysis.
                - Do NOT start every answer with "Considering your age, gender, height, weight and activity level...". Mention profile metrics only when they directly contribute.
                - Do not invent arbitrary numerical targets (no invented weight ranges, no uncalculated calorie/protein targets). Only quote official numbers supplied below.

                MEDICAL SAFETY:
                - Never diagnose diseases, disorders, or medical conditions.
                - Never prescribe medical treatment.
                - Use cautious lifestyle language ("may be relevant", "could support", "based on the information you provided").

                LANGUAGE & TONE:
                - Use clean, simple, and formal English. Avoid overly dense or difficult academic vocabulary.
                - Write short, clear sentences that are easy and quick to read.
                - Respond in the EXACT same language as the user's latest question.

                ZERO FLUFF & FILLER:
                - Cut out all unnecessary filler words and get straight to the point in the very first sentence.
                - Never repeat boilerplate intro phrases such as "You have not selected a goal yet" or "Please choose a goal through the normal Choose Goal flow".

                STRICT OUTPUT CONSTRAINTS:
                - Cap your entire response to 50–80 words maximum.
                - Provide 1 direct opening sentence, followed by 2 to 3 concise bullet points (-) for recommendations instead of dense text blocks.
                - TUI CLEANLINESS: Do not use Markdown asterisks (**bold**). Use clean plaintext with hyphen bullets (-).
                """);

        if (user != null) {
            sb.append("\nUSER PROFILE (CONTEXT ONLY):\n");
            sb.append("Age: ").append(user.getAge() != null ? user.getAge() : "Not specified").append('\n')
                    .append("Gender: ").append(user.getGender() != null ? user.getGender() : "Not specified").append('\n')
                    .append("Height: ").append(user.getHeightCm() != null && user.getHeightCm() > 0 ? String.format(Locale.ROOT, "%.0f cm", user.getHeightCm()) : "Not specified").append('\n')
                    .append("Weight: ").append(user.getWeightKg() != null && user.getWeightKg() > 0 ? String.format(Locale.ROOT, "%.1f kg", user.getWeightKg()) : "Not specified").append('\n')
                    .append("Activity Level: ").append(user.getActivityLevel() != null ? user.getActivityLevel() : "Not specified").append('\n');
            if (user.getHeightCm() != null && user.getHeightCm() > 0 && user.getWeightKg() != null && user.getWeightKg() > 0) {
                double bmi = user.getWeightKg() / ((user.getHeightCm() / 100.0) * (user.getHeightCm() / 100.0));
                sb.append(String.format(Locale.ROOT, "Calculated BMI: %.1f\n", bmi));
            }
            sb.append('\n');
        }

        if (goal != null) {
            sb.append("CURRENT SELECTED GOAL (INTERNAL CONTEXT ONLY - DO NOT DISPLAY UNLESS ASKED):\n");
            sb.append(goal.getName()).append(" - ").append(goal.getDescription() != null ? goal.getDescription() : "").append("\n\n");
        } else {
            sb.append("CURRENT SELECTED GOAL: None selected yet.\n\n");
        }

        sb.append("OFFICIAL CALCULATED TARGETS (QUOTE ONLY IF RELEVANT TO USER QUESTION):\n");
        if (calorieSummary != null && calorieSummary.bmr > 0) {
            sb.append(String.format(Locale.ROOT, "BMR: %.0f kcal/day | TDEE: %.0f kcal/day", calorieSummary.bmr, calorieSummary.tdee));
            if (calorieRelevant && calorieSummary.suggestedTarget > 0) {
                sb.append(String.format(Locale.ROOT, " | Calorie Target: ~%.0f kcal/day", calorieSummary.suggestedTarget));
            }
            sb.append('\n');
        }
        sb.append(String.format(Locale.ROOT, "Daily Hydration Target: %.1f L/day\n\n", hydrationLiters));

        if (plan != null && plan.getAreas() != null && !plan.getAreas().isEmpty()) {
            sb.append("OFFICIAL PLAN AREAS (INTERNAL CONTEXT ONLY):\n");
            for (PersonalizedPlanResult.AreaItem area : plan.getAreas()) {
                sb.append("• ").append(area.emoji()).append(" ").append(area.category().getName());
                if (area.recommendation() != null) {
                    sb.append(" — Rec: \"").append(area.recommendation().getTitle()).append("\"");
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        if (recentConversation != null && !recentConversation.isEmpty()) {
            sb.append("RECENT CONVERSATION:\n");
            int start = Math.max(0, recentConversation.size() - 6);
            for (int i = start; i < recentConversation.size(); i++) {
                sb.append(recentConversation.get(i)).append('\n');
            }
            sb.append('\n');
        }

        sb.append("USER LATEST QUESTION:\n").append(question.trim())
                .append("\n\nTASK:\nDetermine the user's specific intent from their latest question and answer THAT question directly, accurately, and naturally.");

        return sb.toString();
    }

    private String buildChatPrompt(User user, Goal goal, RecommendationCategory category,
                                   Recommendation recommendation, PersonalizedPlanResult plan,
                                   List<String> recentConversation,
                                   String question) {
        StringBuilder sb = new StringBuilder("""
                You are the LIFEForge AI assistant, part of LIFEForge, a personalized
                health and lifestyle RECOMMENDATION system.

                Answer the user's SPECIFIC question directly, using the context below.
                Be concise, practical, contextual, and directly responsive.

                HARD RULES — MUST FOLLOW:
                - The official LIFEForge recommendation is deterministic and authoritative.
                - Do NOT create, replace, alter, or rank recommendations.
                - Do NOT invent profile data, measurements, targets, diagnoses, medication,
                  or unsupported medical claims.
                - Do NOT invent precise nutrition quantities. Do NOT claim that a specific
                  food, meal, or combination will provide an exact amount of protein,
                  calories, or other nutrients. LIFEForge does not calculate per-food macros.
                  Use qualitative and practical guidance instead (e.g. "protein-rich",
                  "low-fat", "spread protein across meals") without exact gram or calorie
                  figures that LIFEForge has not computed.
                - LIFEForge is a recommendation system, NOT a tracking app. It does not
                  track calories eaten, protein eaten, meals, water consumed, or daily habits.
                  Do NOT instruct the user to "monitor", "log", or "track" their daily intake.
                - Understand conversational context. Pronouns like "it", "this", "that",
                  or "the target" may refer to protein, the recommendation, or a goal
                  mentioned earlier in the conversation.

                RESPONSE STYLE (STRICT RULES):
                - 1. BREVITY: Keep answers strictly under 80–100 words. Never output walls of text.
                - 2. NO PSEUDO-SCIENCE: Strictly avoid buzzwords like "toxin buildup", "flushing toxins", or detox claims. Focus on biological recovery and hydration.
                - 3. NO UNCALIBRATED NUMBERS: Do not invent new gram, milliliter, or calorie figures. Quote or direct the user to their existing calibrated Rule Engine targets.
                - 4. TUI CLEANLINESS: Do not use Markdown asterisks (**bold**). Use clean plaintext with hyphen bullets (-).
                - 5. STRUCTURE: Provide 1 brief sentence explaining the core cause, followed by 2–3 short, actionable bullet points.
                - 6. LANGUAGE MATCHING (CRITICAL):
                  * Always respond in the EXACT same language as the user's latest question.
                  * If the user writes in English, reply STRICTLY in English.
                  * NEVER switch to or output Khmer unless the user explicitly writes in Khmer script or explicitly asks to translate into Khmer.
                  * If the user explicitly asks for Khmer, provide natural, grammatically correct Khmer writing words contiguously without inserting spaces inside words.
                - Do not add unnecessary greetings or filler.

                USER PROFILE:
                """);
        sb.append("Age: ").append(user != null ? user.getAge() : "N/A").append('\n')
                .append("Gender: ").append(user != null ? user.getGender() : "N/A").append('\n')
                .append("Height: ").append(user != null ? user.getHeightCm() : "N/A").append(" cm\n")
                .append("Weight: ").append(user != null ? user.getWeightKg() : "N/A").append(" kg\n")
                .append("Activity Level: ").append(user != null ? user.getActivityLevel() : "N/A").append("\n\n")
                .append("CURRENT GOAL:\n").append(goal != null ? goal.getName() : "Active Goal").append("\n\n")
                .append("CURRENT CATEGORY VIEWED:\n")
                .append(category == null ? "Not specified" : category.getName()).append("\n\n")
                .append("CURRENT LIFEFORGE RECOMMENDATION:\n");
        if (recommendation != null) {
            appendIfPresent(sb, "Title: ", recommendation.getTitle());
            appendIfPresent(sb, "Description: ", recommendation.getDescription());
            appendIfPresent(sb, "Recommended Actions: ", recommendation.getRecommendedActions());
            appendIfPresent(sb, "Suggested Target: ", recommendation.getSuggestedTarget());
            appendIfPresent(sb, "Examples: ", recommendation.getExamples());
            appendIfPresent(sb, "Important Notes: ", recommendation.getImportantNotes());
        }
        if (plan != null && plan.getAreas() != null && !plan.getAreas().isEmpty()) {
            sb.append("\nRELATED LIFEFORGE PLAN AREAS FOR THIS GOAL:\n");
            for (PersonalizedPlanResult.AreaItem area : plan.getAreas()) {
                if (category != null && area.category() != null && area.category().getId() != null
                        && area.category().getId().equals(category.getId())) {
                    continue;
                }
                sb.append("• ").append(area.category() != null ? area.category().getName() : "Area");
                if (area.priority() != null) {
                    sb.append(" [").append(area.priority().getLabel()).append("]");
                }
                if (area.recommendation() != null) {
                    sb.append(": ").append(area.recommendation().getTitle());
                    if (area.recommendation().getSuggestedTarget() != null && !area.recommendation().getSuggestedTarget().isBlank()) {
                        sb.append(" (Target: ").append(area.recommendation().getSuggestedTarget()).append(")");
                    }
                }
                sb.append("\n");
            }
        }
        if (recentConversation != null && !recentConversation.isEmpty()) {
            sb.append("\nRECENT CONVERSATION:\n");
            int start = Math.max(0, recentConversation.size() - 6);
            for (int i = start; i < recentConversation.size(); i++) {
                sb.append(recentConversation.get(i)).append('\n');
            }
        }
        sb.append("\nUSER QUESTION:\n").append(question.trim())
                .append("\n\nTASK:\n")
                .append("The user is viewing the '").append(category != null ? category.getName() : "Recommendation")
                .append("' area for their active goal: '").append(goal != null ? goal.getName() : "Active Goal").append("'.\n")
                .append("The user is encouraged to ask ANY question related to their goal — including nutrition, hydration, daily habits, exercise, sleep, or lifestyle routines.\n")
                .append("Answer the user's question directly, practically, and concisely, tailored to their '")
                .append(goal != null ? goal.getName() : "Active Goal").append("' goal and profile context.\n")
                .append("Ground your answer in the official LIFEForge plan and recommendations above.");
        return sb.toString();
    }

    private String buildPrompt(
            User user,
            Goal goal,
            ActivityLevel activityLevel,
            Recommendation recommendation) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are the AI explanation assistant for LifeForge,
                a personalized health and lifestyle recommendation system.

                TONE AND PERSPECTIVE (MANDATORY):
                - Address the user directly in the second person ("you", "your").
                - Strictly NEVER use third-person medical phrasing (do NOT use "this user", "the user", "the patient", "his", or "her").
                - Do NOT include tautological boilerplate like "LIFEForge selected this because it aligns with your goal".
                - Explain WHY this specific recommendation and target physiologically benefit your body and support your goal.

                FORMAT AND STRUCTURE (STRICT):
                - Ban multi-sentence narrative text blocks.
                - Restrict your output to strictly 3 bullet points, no more and no less.
                - Every bullet point must strictly follow this format:
                  • [Keyword / Tag] : [One clear, concise sentence explaining the physiological impact]
                - Examples:
                  • Protein Synthesis : Sustained protein intake delivers essential amino acids required to stimulate muscle protein synthesis.
                  • Metabolic Regulation : Quality protein exerts a high thermic effect, supporting steady glucose metabolism.
                  • Tissue Adaptation : Consistent distribution across meals ensures your body maintains positive nitrogen balance.
                - Exactly ONE clear, concise sentence per bullet point explaining the physiological impact on your body. Do NOT write multiple sentences in any bullet.

                IMPORTANT GUARDRAILS:
                - Do NOT create a new recommendation.
                - Do NOT change the existing recommendation.
                - Use only the information provided below.
                - The "Suggested Target" given below is the ONLY official
                  numeric target. Do NOT calculate, cite, or mention any
                  other numeric target, formula, or range (for example a
                  different g/kg/day protein formula, a different calorie
                  number, or a different water/hydration amount) even if
                  it is common general knowledge. If you reference a
                  target at all, quote the "Suggested Target" value below
                  verbatim.
                - Do not invent medical diagnoses.
                - Do not provide dangerous or extreme dieting advice.

                USER PROFILE:
                """);

        sb.append("- Age: ")
                .append(user.getAge())
                .append("\n");

        sb.append("- Gender: ")
                .append(user.getGender())
                .append("\n");

        sb.append("- Height: ")
                .append(user.getHeightCm())
                .append(" cm\n");

        sb.append("- Weight: ")
                .append(user.getWeightKg())
                .append(" kg\n");

        sb.append("- Activity Level: ")
                .append(activityLevel)
                .append("\n\n");

        sb.append("SELECTED GOAL:\n");
        sb.append(goal.getName())
                .append("\n\n");

        sb.append("EXISTING RECOMMENDATION:\n");

        appendIfPresent(
                sb,
                "- Title: ",
                recommendation.getTitle()
        );

        appendIfPresent(
                sb,
                "- Description: ",
                recommendation.getDescription()
        );

        appendIfPresent(
                sb,
                "- Recommended Actions: ",
                recommendation.getRecommendedActions()
        );

        appendIfPresent(
                sb,
                "- Suggested Target: ",
                recommendation.getSuggestedTarget()
        );

        appendIfPresent(
                sb,
                "- Examples: ",
                recommendation.getExamples()
        );

        appendIfPresent(
                sb,
                "- Important Notes: ",
                recommendation.getImportantNotes()
        );

        sb.append("""

                TASK:
                Explain directly to the user in the second person ("you", "your") why this recommendation
                works for their body, using strictly 3 bullet points in the "• [Keyword / Tag] : [One clear, concise sentence explaining the physiological impact]" format.
                """);

        return sb.toString();
    }

    private void appendIfPresent(
            StringBuilder sb,
            String label,
            String value) {

        if (value != null && !value.isBlank()) {
            sb.append(label)
                    .append(value)
                    .append("\n");
        }
    }

    public static boolean isKhmerRequest(String q) {
        if (q == null) return false;
        String lower = q.toLowerCase(Locale.ROOT);
        if (lower.contains("khmer") || lower.contains("cambodia") || lower.contains("translate to khmer")) {
            return true;
        }
        return containsKhmer(q);
    }

    public static boolean containsKhmer(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u1780' && c <= '\u17FF') {
                return true;
            }
        }
        return false;
    }

    public static String sanitizeKhmerText(String text) {
        if (text == null || text.isEmpty()) return text;
        String s = text.replaceAll("\\u17D2\\s+", "\u17D2");
        s = s.replaceAll("\\s+([\\u17B4-\\u17D3\\u17DD])", "$1");
        s = s.replaceAll("([\\u17B7-\\u17BD\\u17C6\\u17CB])\\s+([\\u1780-\\u17B3])", "$1$2");
        return s;
    }

    public String callOllamaChat(List<Map<String, String>> messages) throws Exception {
        String baseUrl = AppConfig.getOllamaBaseUrl();
        String model = AppConfig.getOllamaModel();

        if (VERBOSE) {
            System.out.println("[LifeForge AI] Chat URL: " + baseUrl + "/api/chat");
            System.out.println("[LifeForge AI] Model: " + model);
        }

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":").append(jsonString(model)).append(",");
        json.append("\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) json.append(",");
            Map<String, String> m = messages.get(i);
            json.append("{");
            json.append("\"role\":").append(jsonString(m.get("role"))).append(",");
            json.append("\"content\":").append(jsonString(m.get("content")));
            json.append("}");
        }
        json.append("],");
        json.append("\"stream\":false,");
        json.append("\"options\":{");
        json.append("\"num_predict\":120,");
        json.append("\"temperature\":0.3,");
        json.append("\"top_p\":0.9");
        json.append("}");
        json.append("}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        if (VERBOSE) {
            System.out.println("[LifeForge AI] Calling Ollama /api/chat asynchronously...");
        }

        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        HttpResponse<String> response = future.get(15, java.util.concurrent.TimeUnit.SECONDS);

        if (VERBOSE) {
            System.out.println("[LifeForge AI] HTTP Status: " + response.statusCode());
        }

        if (response.statusCode() != 200) {
            if (VERBOSE) {
                System.err.println("[LifeForge AI] Ollama chat error body: " + response.body());
            }
            return null;
        }

        String body = response.body();
        String extracted = extractChatResponseContent(body);
        if (extracted == null && VERBOSE) {
            System.err.println("[LifeForge AI] Could not extract content from Ollama chat response: " + body);
        }
        return extracted;
    }

    private String extractChatResponseContent(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String regex = "\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJsonString(matcher.group(1));
        }
        return extractJsonStringField(json, "response");
    }

    private String callOllama(String prompt) throws Exception {
        return callOllamaChat(List.of(
                Map.of("role", "user", "content", prompt)
        ));
    }

    private String jsonString(String value) {

        if (value == null) {
            return "\"\"";
        }

        return "\""
                + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                + "\"";
    }

    /**
     * Extracts a JSON string field while allowing whitespace
     * around the colon.
     */
    private String extractJsonStringField(
            String json,
            String field) {

        if (json == null || json.isBlank()) {
            return null;
        }

        String regex =
                "\"" + Pattern.quote(field)
                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";

        Pattern pattern =
                Pattern.compile(regex);

        Matcher matcher =
                pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        return unescapeJsonString(
                matcher.group(1)
        );
    }

    private String unescapeJsonString(String value) {

        StringBuilder result =
                new StringBuilder();

        boolean escaping = false;

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (!escaping) {

                if (c == '\\') {
                    escaping = true;
                } else {
                    result.append(c);
                }

                continue;
            }

            switch (c) {

                case 'n' ->
                        result.append('\n');

                case 'r' ->
                        result.append('\r');

                case 't' ->
                        result.append('\t');

                case 'b' ->
                        result.append('\b');

                case 'f' ->
                        result.append('\f');

                case '"' ->
                        result.append('"');

                case '\\' ->
                        result.append('\\');

                case '/' ->
                        result.append('/');

                default ->
                        result.append(c);
            }

            escaping = false;
        }

        if (escaping) {
            result.append('\\');
        }

        return result.toString();
    }
}