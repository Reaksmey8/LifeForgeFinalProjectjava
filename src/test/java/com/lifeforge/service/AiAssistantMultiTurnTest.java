package com.lifeforge.service;

import com.lifeforge.model.Gender;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.RecommendationCategory;
import com.lifeforge.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AiAssistantMultiTurnTest {

    private AiExplanationService aiService;
    private User testUser;
    private Goal testGoal;
    private RecommendationCategory testCategory;

    @BeforeEach
    public void setUp() {
        aiService = new AiExplanationService();
        aiService.clearConversationHistory();

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setAge(28);
        testUser.setGender(Gender.MALE);
        testUser.setHeightCm(175.0);
        testUser.setWeightKg(70.0);

        testGoal = new Goal();
        testGoal.setId(10L);
        testGoal.setName("Hypertrophy & Strength");

        testCategory = new RecommendationCategory(20L, "Functional Mobility", "Mobility and flexibility routines", null, 0);
    }

    @Test
    public void testUserGettersAndBmiCalculation() {
        assertEquals(175.0, testUser.getHeight(), 0.001);
        assertEquals(70.0, testUser.getWeight(), 0.001);
        // BMI: 70 / (1.75 * 1.75) = 70 / 3.0625 = 22.857...
        assertEquals(22.9, testUser.getBmi(), 0.1);

        // Edge case: user with null or 0 height/weight
        User emptyUser = new User();
        assertEquals(0.0, emptyUser.getHeight(), 0.001);
        assertEquals(0.0, emptyUser.getWeight(), 0.001);
        assertEquals(0.0, emptyUser.getBmi(), 0.001);
    }

    @Test
    public void testDynamicSystemPromptGenerationUniversalContext() {
        String systemPrompt = aiService.buildSystemPrompt(testUser, testGoal, testCategory);

        // Check required persona
        assertTrue(systemPrompt.contains("You are the LIFEForge Health Assistant."),
                "System prompt must contain 'You are the LIFEForge Health Assistant.'");

        // Check dynamic goal & category injection
        assertTrue(systemPrompt.contains("Active Goal: 'Hypertrophy & Strength'"),
                "Must dynamically contain active goal");
        assertTrue(systemPrompt.contains("Active Category: 'Functional Mobility'"),
                "Must dynamically contain active category");

        // Check user metrics injection
        assertTrue(systemPrompt.contains("Age 28"), "Must contain user age");
        assertTrue(systemPrompt.contains("Height 175.0 cm"), "Must contain user height");
        assertTrue(systemPrompt.contains("Weight 70.0 kg"), "Must contain user weight");
        assertTrue(systemPrompt.contains("BMI 22.9"), "Must contain user calculated BMI");

        // Check the 4 mandatory rules
        assertTrue(systemPrompt.contains("1. Directly answer the user's specific query"),
                "Must enforce Rule 1 (direct query answering)");
        assertTrue(systemPrompt.contains("2. Support all lifestyle categories (Exercise, Nutrition, Sleep, Hydration, Mental Wellness, Posture, etc.)"),
                "Must enforce Rule 2 (all lifestyle categories)");
        assertTrue(systemPrompt.contains("3. Clinical BMI reference: <18.5 is Underweight, 18.5-24.9 is Normal, 25.0-29.9 is Overweight, >=30.0 is Obese."),
                "Must enforce Rule 3 (clinical BMI reference ranges)");
        assertTrue(systemPrompt.contains("4. Keep responses grounded, actionable, and under 3 concise sentences unless explicitly asked for a list."),
                "Must enforce Rule 4 (concise and grounded responses)");
    }

    @Test
    public void testDynamicSystemPromptFallbackWhenGoalOrCategoryNull() {
        String systemPrompt = aiService.buildSystemPrompt(testUser, null, null);

        assertFalse(systemPrompt.contains("Active Goal: 'General Wellness'"),
                "Must NOT assume or display 'General Wellness' as the active goal");
        assertFalse(systemPrompt.contains("Current Active Goal"),
                "Must NOT show 'Current Active Goal'");
        assertTrue(systemPrompt.contains("No goal selected yet"),
                "Must recognize that no goal has been selected yet");
        assertTrue(systemPrompt.contains("Active Category: 'General Health'"),
                "Should fallback to 'General Health' when category is null");
    }

    @Test
    public void testAiAssistantGoalContextWhenNoGoalSelectedYet() {
        // 1. Verify system prompt behavior
        String systemPrompt = aiService.buildSystemPrompt(testUser, null, null);
        assertFalse(systemPrompt.contains("Active Goal: 'General Wellness'"));
        assertFalse(systemPrompt.contains("Current Active Goal"));
        assertTrue(systemPrompt.contains("No goal selected yet"));
        assertTrue(systemPrompt.contains("Age 28"));
        assertTrue(systemPrompt.contains("Height 175.0 cm"));
        assertTrue(systemPrompt.contains("Weight 70.0 kg"));
        assertTrue(systemPrompt.contains("BMI 22.9"));

        // 2. Verify AI Assistant matching goal guidance before goal selection
        AiChatResponse resp = aiService.globalFallback(
                testUser, null, null, null, 2.5, false, "Which goal is matching with me?");
        assertNotNull(resp);
        assertTrue(resp.text.contains("1. Build Lean Muscle"), "Must list 'Build Lean Muscle'");
        assertTrue(resp.text.contains("2. Lose Weight / Fat Loss"), "Must list 'Lose Weight / Fat Loss'");
        assertTrue(resp.text.contains("3. General Health & Vitality"), "Must list 'General Health & Vitality'");
        assertTrue(resp.text.contains("Given your"), "Must recommend best goal based on metrics");
        assertTrue(resp.text.contains("Press [ESC] to return to the Main Menu, then press [2] to lock in your goal."),
                "Must provide navigation instructions");
        assertFalse(resp.text.contains("Please choose a goal through the normal Choose Goal flow"),
                "Must NOT repeat boilerplate Choose Goal flow phrase");
        assertFalse(resp.text.contains("Current Active Goal"),
                "Must NOT show Current Active Goal");
        assertFalse(resp.text.contains("Active Goal: General Wellness"),
                "Must NOT assume or display General Wellness as the active goal");
    }

    @Test
    public void testMultiTurnTurnSequence() {
        AiChatResponse response1 = aiService.chat(testUser, testGoal, testCategory, null, null, List.of(), "How long should I stretch each morning?");

        assertNotNull(response1);
        if (!response1.fromAi) {
            assertTrue(response1.text.startsWith(AiExplanationService.OFFLINE_NOTICE));
        }

        // Verify conversation history was populated
        List<Map<String, String>> history = aiService.getConversationHistory();
        assertTrue(history.size() >= 3, "History should contain system prompt, user prompt, and assistant response");

        // System prompt at index 0
        assertEquals("system", history.get(0).get("role"));
        assertTrue(history.get(0).get("content").contains("You are the LIFEForge Health Assistant."));

        // User message at index 1
        assertEquals("user", history.get(1).get("role"));
        assertEquals("How long should I stretch each morning?", history.get(1).get("content"));

        // Assistant response at index 2
        assertEquals("assistant", history.get(2).get("role"));
        assertNotNull(history.get(2).get("content"));

        // Turn 2
        AiChatResponse response2 = aiService.chat(testUser, testGoal, testCategory, null, null, List.of(), "Can you give me 3 specific stretches?");
        assertNotNull(response2);

        List<Map<String, String>> historyTurn2 = aiService.getConversationHistory();
        // Turn 2 adds 1 user message and 1 assistant message -> total 5 messages
        assertEquals(5, historyTurn2.size());
        assertEquals("user", historyTurn2.get(3).get("role"));
        assertEquals("Can you give me 3 specific stretches?", historyTurn2.get(3).get("content"));
        assertEquals("assistant", historyTurn2.get(4).get("role"));
    }

    @Test
    public void testOfflineResilientFallbackNotice() {
        AiExplanationService offlineAi = new AiExplanationService() {
            @Override
            public String callOllamaChat(List<Map<String, String>> messages) throws Exception {
                throw new java.net.ConnectException("Connection refused: localhost:11434");
            }
        };

        AiChatResponse resp = offlineAi.chat(testUser, testGoal, testCategory, null, null, List.of(), "What are good mobility exercises?");

        assertNotNull(resp);
        assertFalse(resp.fromAi, "Must flag fromAi == false when offline");
        assertTrue(resp.text.startsWith(AiExplanationService.OFFLINE_NOTICE),
                "Response text must start with clean OFFLINE_NOTICE");

        List<Map<String, String>> history = offlineAi.getConversationHistory();
        assertEquals(3, history.size());
        assertEquals("system", history.get(0).get("role"));
        assertEquals("user", history.get(1).get("role"));
        assertEquals("assistant", history.get(2).get("role"));
        assertTrue(history.get(2).get("content").startsWith(AiExplanationService.OFFLINE_NOTICE));
    }

    @Test
    public void testGlobalOfflineResilientFallbackNotice() {
        AiExplanationService offlineAi = new AiExplanationService() {
            @Override
            public String callOllamaChat(List<Map<String, String>> messages) throws Exception {
                throw new java.net.ConnectException("Connection refused: localhost:11434");
            }
        };

        AiChatResponse resp = offlineAi.chatGlobal(testUser, testGoal, null, null, 2.5, false, List.of(), "How much water should I drink today?");

        assertNotNull(resp);
        assertFalse(resp.fromAi);
        assertTrue(resp.text.startsWith(AiExplanationService.OFFLINE_NOTICE));

        List<Map<String, String>> history = offlineAi.getConversationHistory();
        assertEquals(3, history.size());
        assertEquals("system", history.get(0).get("role"));
        assertEquals("user", history.get(1).get("role"));
        assertEquals("assistant", history.get(2).get("role"));
        assertTrue(history.get(2).get("content").startsWith(AiExplanationService.OFFLINE_NOTICE));
    }

    @Test
    public void testClearConversationHistory() {
        aiService.chat(testUser, testGoal, testCategory, null, null, List.of(), "Hello assistant");
        assertFalse(aiService.getConversationHistory().isEmpty());

        aiService.clearConversationHistory();
        assertTrue(aiService.getConversationHistory().isEmpty(), "Conversation history must be completely empty after clear");
    }

    @Test
    public void testGlobalAssistantIntentRoutingBmrAndTdee() {
        // Concept question
        AiChatResponse respConcept = aiService.globalFallback(
                testUser, null, null, null, 2.5, false, "What is BMR?");
        assertNotNull(respConcept);
        assertTrue(respConcept.text.contains("Basal Metabolic Rate"), "Must explain Basal Metabolic Rate");
        assertFalse(respConcept.text.contains("50-70 kg"), "Must not hallucinate arbitrary weight range");

        // User specific question
        testUser.setActivityLevel(com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE);
        AiChatResponse respUserBmr = aiService.globalFallback(
                testUser, null, null, null, 2.5, false, "What is my BMR and TDEE?");
        assertNotNull(respUserBmr);
        assertTrue(respUserBmr.text.contains("Basal Metabolic Rate (BMR)"), "Must provide estimated BMR");
        assertTrue(respUserBmr.text.contains("Total Daily Energy Expenditure (TDEE)"), "Must provide estimated TDEE");
    }

    @Test
    public void testGlobalAssistantIntentRoutingGoalComparison() {
        AiChatResponse respComp = aiService.globalFallback(
                testUser, null, null, null, 2.5, false,
                "What is the difference between Gain Weight and Build Muscle?");
        assertNotNull(respComp);
        assertTrue(respComp.text.contains("Gain Weight"), "Must mention Gain Weight");
        assertTrue(respComp.text.contains("Build Muscle"), "Must mention Build Muscle");
        assertTrue(respComp.text.contains("resistance training") || respComp.text.contains("progressive overload"),
                "Must explain hypertrophy/training difference");
    }

    @Test
    public void testGlobalAssistantIntentRoutingBreakfastIdeas() {
        AiChatResponse respBreakfast = aiService.globalFallback(
                testUser, null, null, null, 2.5, false, "What should I eat for breakfast?");
        assertNotNull(respBreakfast);
        assertTrue(respBreakfast.text.contains("breakfast") || respBreakfast.text.contains("Protein"),
                "Must provide breakfast recommendations");
        assertFalse(respBreakfast.text.contains("50-70 kg"), "Must not invent weight range for nutrition question");
    }

    @Test
    public void testGlobalAssistantIntentRoutingFatigue() {
        AiChatResponse respTired = aiService.globalFallback(
                testUser, null, null, null, 2.5, false, "Why am I always tired?");
        assertNotNull(respTired);
        assertTrue(respTired.text.contains("Sleep") || respTired.text.contains("Hydration"),
                "Must address lifestyle factors for fatigue");
        assertTrue(respTired.text.contains("healthcare professional"),
                "Must include non-diagnostic medical disclaimer");
    }

    @Test
    public void testGlobalAssistantIntentRoutingProfile() {
        AiChatResponse respProfile = aiService.globalFallback(
                testUser, null, null, null, 2.5, false, "Can you tell me about my profile?");
        assertNotNull(respProfile);
        assertTrue(respProfile.text.contains("28"), "Must contain age");
        assertTrue(respProfile.text.contains("175.0"), "Must contain height");
        assertTrue(respProfile.text.contains("70.0"), "Must contain weight");
        assertTrue(respProfile.text.contains("22.9"), "Must contain BMI");
        assertFalse(respProfile.text.contains("50-70 kg"), "Must not invent weight range");
    }

    @Test
    public void testGlobalAssistantIntentRoutingOfficialGoalsList() {
        AiChatResponse respGoals = aiService.globalFallback(
                testUser, null, null, null, 2.5, false, "What are the official goals in LIFEForge?");
        assertNotNull(respGoals);
        assertTrue(respGoals.text.contains("Build Muscle"), "Must list Build Muscle");
        assertTrue(respGoals.text.contains("Lose Weight"), "Must list Lose Weight");
        assertTrue(respGoals.text.contains("Improve Fitness"), "Must list Improve Fitness");
        assertTrue(respGoals.text.contains("General Wellness"), "Must list General Wellness");
    }

    @Test
    public void testBuildGlobalAssistantPromptIntentDirectives() {
        String prompt = aiService.buildGlobalAssistantPrompt(
                testUser, null, null, null, 2.5, false, List.of(), "What should I eat for breakfast?");
        assertTrue(prompt.contains("determine what the user is actually asking about"),
                "Prompt must instruct determining user intent");
        assertTrue(prompt.contains("Do not invent arbitrary numerical targets"),
                "Prompt must forbid arbitrary numeric targets");
        assertTrue(prompt.contains("Never diagnose diseases"),
                "Prompt must forbid medical diagnoses");
        assertTrue(prompt.contains("CURRENT SELECTED GOAL: None selected yet."),
                "Prompt must recognize when no goal is selected");
        assertTrue(prompt.contains("clean, simple, and formal English"),
                "Prompt must instruct using clean, simple, and formal English");
        assertTrue(prompt.contains("ZERO FLUFF & FILLER"),
                "Prompt must instruct zero fluff and filler");
        assertTrue(prompt.contains("50–80 words maximum"),
                "Prompt must cap response to 50-80 words maximum");
    }

    @Test
    public void testHistoryPruningToEightMessages() {
        // Simulate adding many turns to global assistant
        for (int i = 1; i <= 10; i++) {
            aiService.chatGlobal(testUser, null, null, null, 2.5, false, List.of(), "Question " + i);
        }
        List<Map<String, String>> history = aiService.getConversationHistory();
        // System prompt (1) + at most 8 message turns (4 exchanges) = 9
        assertTrue(history.size() <= 9, "Conversation history must be pruned to system prompt + at most 8 messages, was: " + history.size());
        assertEquals("system", history.get(0).get("role"), "First message must be system prompt");
    }

    @Test
    public void testRecommendationDetailGoalConflictGainWeightWhileOnLoseWeight() {
        Goal loseGoal = new Goal(2L, "LOSE_WEIGHT", "Lose Weight", "Caloric deficit and cardio", true);
        RecommendationCategory nutCat = new RecommendationCategory(1L, "Nutrition", "Dietary guidance", null, 1);
        Recommendation rec = new Recommendation(
                10L, 2L, 1L, com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE,
                "Eat More Protein",
                "High protein increases satiety and preserves lean tissue during a caloric deficit.",
                "Consume 25-30g protein per main meal.",
                "1.6-2.2g per kg",
                "Eggs, chicken breast, tofu, lentils, Greek yogurt",
                "Prioritize whole protein sources over powders when possible."
        );

        // User asks to gain 3kg in 1 week while active goal is Lose Weight
        AiChatResponse resp = aiService.chatFallback(
                testUser, loseGoal, nutCat, rec, "I want to gain 3kg in 1 week. How?");

        assertNotNull(resp);
        assertFalse(resp.fromAi);
        // 1. Clearly explains current goal is Lose Weight while question is about gaining weight
        assertTrue(resp.text.contains("Lose Weight"), "Must state current goal is Lose Weight");
        assertTrue(resp.text.contains("gaining weight"), "Must recognize question is about gaining weight");

        // 2. Safe general guidance instead of extreme rapid-weight change plan
        assertTrue(resp.text.contains("unrealistic and unsafe") || resp.text.contains("unsafe"),
                "Must warn against rapid weight changes");
        assertTrue(resp.text.contains("gradual") || resp.text.contains("0.25–0.5 kg"),
                "Must recommend safe gradual adjustments");

        // 3. Goal Selection guidance through Choose Goal flow
        assertTrue(resp.text.contains("Choose Goal"), "Must mention Choose Goal flow");
        assertTrue(resp.text.contains("[ESC]"), "Must mention ESC navigation to change goal");

        // 4. Does not invent arbitrary calorie/weight targets
        assertFalse(resp.text.contains("5000 kcal") || resp.text.contains("4000 kcal"));
    }

    @Test
    public void testRecommendationDetailGoalConflictCanIGainWeightInstead() {
        Goal loseGoal = new Goal(2L, "LOSE_WEIGHT", "Lose Weight", "Caloric deficit and cardio", true);
        RecommendationCategory nutCat = new RecommendationCategory(1L, "Nutrition", "Dietary guidance", null, 1);
        Recommendation rec = new Recommendation(
                10L, 2L, 1L, com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE,
                "Eat More Protein",
                "High protein increases satiety and preserves lean tissue.",
                "Consume 25-30g protein per main meal.",
                "1.6g/kg",
                "Eggs, chicken breast, Greek yogurt",
                "Notes"
        );

        AiChatResponse resp = aiService.chatFallback(
                testUser, loseGoal, nutCat, rec, "Can I gain weight instead?");

        assertNotNull(resp);
        assertTrue(resp.text.contains("Lose Weight"));
        assertTrue(resp.text.contains("gaining weight"));
        assertTrue(resp.text.contains("Choose Goal"));
        assertTrue(resp.text.contains("[ESC]"));
    }

    @Test
    public void testRecommendationDetailAnswerActualQuestionBreakfast() {
        Goal loseGoal = new Goal(2L, "LOSE_WEIGHT", "Lose Weight", "Caloric deficit", true);
        RecommendationCategory nutCat = new RecommendationCategory(1L, "Nutrition", "Dietary guidance", null, 1);
        Recommendation rec = new Recommendation(
                10L, 2L, 1L, com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE,
                "Eat More Protein",
                "Supports muscle preservation and satiety.",
                "Include protein at every meal.",
                "30g per meal",
                "Eggs, Greek yogurt, tofu scramble, protein oats",
                "Notes"
        );

        AiChatResponse resp = aiService.chatFallback(
                testUser, loseGoal, nutCat, rec, "What should I eat for breakfast?");

        assertNotNull(resp);
        // Answers the breakfast/food question directly
        assertTrue(resp.text.contains("Eggs") || resp.text.contains("Greek yogurt") || resp.text.contains("Suggested Foods"));
        assertTrue(resp.text.contains("Eat More Protein"));
    }

    @Test
    public void testRecommendationDetailAnswerActualQuestionWhyRecommendation() {
        Goal loseGoal = new Goal(2L, "LOSE_WEIGHT", "Lose Weight", "Caloric deficit", true);
        RecommendationCategory nutCat = new RecommendationCategory(1L, "Nutrition", "Dietary guidance", null, 1);
        Recommendation rec = new Recommendation(
                10L, 2L, 1L, com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE,
                "Eat More Protein",
                "High protein increases satiety and preserves lean muscle during caloric deficit.",
                "Include protein at each meal.",
                "1.6g/kg",
                "Chicken, fish, tofu, lentils",
                "Notes"
        );

        AiChatResponse resp = aiService.chatFallback(
                testUser, loseGoal, nutCat, rec, "Why should I eat more protein?");

        assertNotNull(resp);
        assertTrue(resp.text.contains("Why This Recommendation Fits") || resp.text.contains("satiety and preserves lean muscle"));
        assertTrue(resp.text.contains("Eat More Protein"));
    }

    @Test
    public void testRecommendationDetailPromptIncludesGoalConflictAndActualQuestionDirectives() {
        Goal loseGoal = new Goal(2L, "LOSE_WEIGHT", "Lose Weight", "Caloric deficit", true);
        RecommendationCategory nutCat = new RecommendationCategory(1L, "Nutrition", "Dietary guidance", null, 1);
        Recommendation rec = new Recommendation(
                10L, 2L, 1L, com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE,
                "Eat More Protein",
                "High protein increases satiety.",
                "Include protein at each meal.",
                "1.6g/kg",
                "Eggs, fish, tofu",
                "Notes"
        );

        String prompt = aiService.buildSystemPrompt(testUser, loseGoal, nutCat, rec);
        assertNotNull(prompt);
        assertTrue(prompt.contains("Active Recommendation: 'Eat More Protein'"));
        assertTrue(prompt.contains("5. Directly answer the user's ACTUAL question"));
        assertTrue(prompt.contains("6. GOAL CONFLICT RECOGNITION:"));
        assertTrue(prompt.contains("Do NOT automatically change their selected goal"));
        assertTrue(prompt.contains("Do NOT pretend they are already using that other goal"));
        assertTrue(prompt.contains("Provide safe, useful general guidance"));
        assertTrue(prompt.contains("normal Choose Goal flow"));
        assertTrue(prompt.contains("Do not invent new calorie, protein, hydration, weight, or exercise targets"));
    }
}
