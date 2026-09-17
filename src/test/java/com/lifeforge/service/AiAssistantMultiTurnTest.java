package com.lifeforge.service;

import com.lifeforge.model.Gender;
import com.lifeforge.model.Goal;
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

        assertTrue(systemPrompt.contains("Active Goal: 'General Wellness'"),
                "Should fallback to 'General Wellness' when goal is null");
        assertTrue(systemPrompt.contains("Active Category: 'General Health'"),
                "Should fallback to 'General Health' when category is null");
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
}
