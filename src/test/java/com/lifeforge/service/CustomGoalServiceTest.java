package com.lifeforge.service;

import com.lifeforge.Session;
import com.lifeforge.controller.GoalController;
import com.lifeforge.model.Goal;
import com.lifeforge.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class CustomGoalServiceTest {

    private CustomGoalService customGoalService;
    private List<Goal> catalogGoals;
    private User testUser;

    @BeforeEach
    public void setUp() {
        customGoalService = new CustomGoalService();
        catalogGoals = List.of(
                new Goal(1L, "LOSE_WEIGHT", "Lose Weight", "Burn fat and reduce calorie intake", true),
                new Goal(2L, "GAIN_WEIGHT", "Gain Weight", "Build healthy body mass", true),
                new Goal(3L, "BUILD_MUSCLE", "Build Muscle", "Strength training and hypertrophy", true),
                new Goal(4L, "IMPROVE_FITNESS", "Improve Fitness", "Cardiovascular endurance and conditioning", true),
                new Goal(5L, "IMPROVE_SKIN_HEALTH", "Improve Skin Health", "Hydration and dermatological wellness", true),
                new Goal(6L, "IMPROVE_SLEEP", "Improve Sleep", "Circadian rhythm and recovery", true),
                new Goal(7L, "GENERAL_WELLNESS", "General Wellness", "Holistic vitality and healthy habits", true),
                new Goal(8L, "POSTURE_CORRECTION", "Posture Correction", "Spine alignment and ergonomic habits", true)
        );

        testUser = new User();
        testUser.setId(100L);
        testUser.setUsername("runner_test");
        testUser.setAge(28);
        testUser.setHeightCm(175.0);
        testUser.setWeightKg(70.0);
    }

    @Test
    @DisplayName("Running and 5K endurance goals map to IMPROVE_FITNESS baseline with 5 pillars")
    public void testRunningGoalMapping() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("I want to prepare for a 5k marathon and improve endurance", testUser, catalogGoals);

        assertNotNull(result);
        assertTrue(result.isSupported());
        assertNull(result.getScopeNotice());
        assertEquals("IMPROVE_FITNESS", result.getBaselineGoal().getCode());
        assertTrue(result.getSynthesizedGoal().getName().contains("(Custom)"));
        assertEquals(5, result.getPillars().size());

        List<String> pillarNames = result.getPillars().stream()
                .map(CustomGoalService.PillarGuidance::getPillarName)
                .toList();
        assertTrue(pillarNames.contains("Exercise"));
        assertTrue(pillarNames.contains("Hydration"));
        assertTrue(pillarNames.contains("Nutrition"));
        assertTrue(pillarNames.contains("Sleep & Recovery"));
        assertTrue(pillarNames.contains("Daily Habits"));
    }

    @Test
    @DisplayName("Muscle and gym bulk goals map to BUILD_MUSCLE baseline")
    public void testMuscleGoalMapping() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("Bulk up chest muscle and strength lift in gym", testUser, catalogGoals);

        assertNotNull(result);
        assertTrue(result.isSupported());
        assertEquals("BUILD_MUSCLE", result.getBaselineGoal().getCode());
        assertNotNull(result.getContextSummary());
    }

    @Test
    @DisplayName("Fat loss and calorie burn goals map to LOSE_WEIGHT baseline")
    public void testWeightLossGoalMapping() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("Burn belly fat and lean down for summer", testUser, catalogGoals);

        assertNotNull(result);
        assertTrue(result.isSupported());
        assertEquals("LOSE_WEIGHT", result.getBaselineGoal().getCode());
    }

    @Test
    @DisplayName("Posture and desk work back pain map to POSTURE_CORRECTION baseline")
    public void testPostureGoalMapping() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("Fix posture slouch from long desk sitting and relieve back pain", testUser, catalogGoals);

        assertNotNull(result);
        assertTrue(result.isSupported());
        assertEquals("POSTURE_CORRECTION", result.getBaselineGoal().getCode());
    }

    @Test
    @DisplayName("Sleep, bedtime, and circadian rhythm map to IMPROVE_SLEEP baseline")
    public void testSleepGoalMapping() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("Fix circadian rhythm, overcome insomnia and get restful sleep", testUser, catalogGoals);

        assertNotNull(result);
        assertTrue(result.isSupported());
        assertEquals("IMPROVE_SLEEP", result.getBaselineGoal().getCode());
    }

    @Test
    @DisplayName("Skin radiance and dermal health map to IMPROVE_SKIN_HEALTH baseline")
    public void testSkinGoalMapping() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("Clear facial skin acne and improve natural glow", testUser, catalogGoals);

        assertNotNull(result);
        assertTrue(result.isSupported());
        assertEquals("IMPROVE_SKIN_HEALTH", result.getBaselineGoal().getCode());
    }

    @Test
    @DisplayName("Mountain trekking and hiking map to IMPROVE_FITNESS baseline")
    public void testHikingGoalMapping() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("Trek and hike steep mountains next month", testUser, catalogGoals);

        assertNotNull(result);
        assertTrue(result.isSupported());
        assertEquals("IMPROVE_FITNESS", result.getBaselineGoal().getCode());
    }

    @Test
    @DisplayName("Clinical and medical claims trigger ethical scope boundary notice")
    public void testMedicalClaimOutOfScope() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("Cure severe diabetes and cancer with medication prescription", testUser, catalogGoals);

        assertNotNull(result);
        assertFalse(result.isSupported());
        assertNotNull(result.getScopeNotice());
        assertTrue(result.getScopeNotice().contains("outside LIFEForge's lifestyle wellness scope"));
        // System still provides foundational lifestyle baseline safely
        assertNotNull(result.getBaselineGoal());
    }

    @Test
    @DisplayName("Professional sports contracts trigger scope boundary notice")
    public void testProAthleteContractOutOfScope() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("Train to sign a contract with the NBA and win super bowl", testUser, catalogGoals);

        assertNotNull(result);
        assertFalse(result.isSupported());
        assertNotNull(result.getScopeNotice());
    }

    @Test
    @DisplayName("Null or empty input falls back gracefully to GENERAL_WELLNESS")
    public void testBlankInputFallback() {
        CustomGoalService.CustomGoalAnalysisResult result =
                customGoalService.analyze("   ", testUser, catalogGoals);

        assertNotNull(result);
        assertTrue(result.isSupported());
        assertEquals("GENERAL_WELLNESS", result.getBaselineGoal().getCode());
        assertNotNull(result.getSynthesizedGoal());
    }

    @Test
    @DisplayName("Session and GoalController prioritize custom goal over stored database goal")
    public void testSessionCustomGoalPriority() {
        Session session = new Session();
        session.setCurrentUser(testUser);

        Goal defaultDbGoal = new Goal(1L, "LOSE_WEIGHT", "Lose Weight", "Burn fat", true);
        Goal customGoal = new Goal(4L, "IMPROVE_FITNESS", "Run 5K Marathon (Custom)", "Cardio stamina", true);

        // Simulated GoalService mock / anonymous implementation
        com.lifeforge.dao.GoalDao gDao = new com.lifeforge.dao.GoalDao();
        com.lifeforge.dao.UserGoalDao ugDao = new com.lifeforge.dao.UserGoalDao();
        GoalService gService = new GoalService(gDao, ugDao) {
            @Override
            public Optional<Goal> getCurrentGoalForUser(Long userId) throws SQLException {
                return Optional.of(defaultDbGoal);
            }
        };

        GoalController controller = new GoalController(gService, session);

        // Before setting custom goal: returns database goal
        Optional<Goal> before = controller.getCurrentGoal();
        assertTrue(before.isPresent());
        assertEquals("Lose Weight", before.get().getName());

        // Set custom goal in session
        session.setCustomGoal(customGoal);

        // After setting custom goal: returns custom synthesized goal
        Optional<Goal> after = controller.getCurrentGoal();
        assertTrue(after.isPresent());
        assertEquals("Run 5K Marathon (Custom)", after.get().getName());
        assertEquals("IMPROVE_FITNESS", after.get().getCode());

        // Clearing custom goal or session reset
        session.clearCustomGoal();
        Optional<Goal> reset = controller.getCurrentGoal();
        assertTrue(reset.isPresent());
        assertEquals("Lose Weight", reset.get().getName());
    }
}
