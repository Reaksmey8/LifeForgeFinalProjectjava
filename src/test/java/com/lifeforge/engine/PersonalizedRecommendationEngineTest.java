package com.lifeforge.engine;

import com.lifeforge.dao.RecommendationCategoryDao;
import com.lifeforge.dao.RecommendationDao;
import com.lifeforge.model.*;
import com.lifeforge.service.*;
import com.lifeforge.service.CalorieService;
import com.lifeforge.service.RecommendationPriorityResolver;
import com.lifeforge.view.LifeForge;
import com.lifeforge.view.ScreenKit;
import com.lifeforge.view.ScreenKit.Line;
import com.lifeforge.view.Theme;
import com.lifeforge.util.CalorieCalculator;
import com.lifeforge.util.HydrationCalculator;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PersonalizedRecommendationEngineTest {

    private User sampleUser;
    private Goal muscleGoal;
    private Goal skinGoal;
    private Goal weightGoal;

    @BeforeAll
    public static void setup() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    @BeforeEach
    public void setUp() {
        sampleUser = new User();
        sampleUser.setId(10L);
        sampleUser.setUsername("testuser");
        sampleUser.setAge(28);
        sampleUser.setGender(Gender.MALE);
        sampleUser.setWeightKg(75.0);
        sampleUser.setHeightCm(178.0);
        sampleUser.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        muscleGoal = new Goal(1L, "BUILD_MUSCLE", "Build Muscle", "Focus on hypertrophy and strength", true);
        skinGoal = new Goal(2L, "SKIN_HEALTH", "Improve Skin Health", "Dermatological wellness and hydration", true);
        weightGoal = new Goal(3L, "LOSE_WEIGHT", "Lose Weight", "Sustainable caloric deficit and cardio", true);
    }

    @Test
    public void testRecommendationPriorityResolver() {
        RecommendationPriorityResolver resolver = new RecommendationPriorityResolver();

        // Muscle Gain: Fitness and Nutrition should be HIGH
        RecommendationCategory fitness = new RecommendationCategory(1L, "Fitness", "Workouts", null, 1);
        RecommendationCategory nutrition = new RecommendationCategory(2L, "Nutrition", "Diet", null, 2);
        RecommendationCategory sleep = new RecommendationCategory(3L, "Sleep & Recovery", "Rest", null, 3);
        RecommendationCategory lifestyle = new RecommendationCategory(5L, "Lifestyle", "Daily habits", null, 5);

        assertEquals(RecommendationPriority.HIGH, resolver.resolve(muscleGoal, sampleUser.getActivityLevel(), fitness));
        assertEquals(RecommendationPriority.HIGH, resolver.resolve(muscleGoal, sampleUser.getActivityLevel(), nutrition));
        assertEquals(RecommendationPriority.RECOMMENDED, resolver.resolve(muscleGoal, sampleUser.getActivityLevel(), sleep));
        assertEquals(RecommendationPriority.SUPPORTING, resolver.resolve(muscleGoal, sampleUser.getActivityLevel(), lifestyle));

        // Skin Health: Hydration and Nutrition should be HIGH
        RecommendationCategory hydration = new RecommendationCategory(4L, "Hydration", "Water intake", null, 4);
        assertEquals(RecommendationPriority.HIGH, resolver.resolve(skinGoal, sampleUser.getActivityLevel(), hydration));
        assertEquals(RecommendationPriority.HIGH, resolver.resolve(skinGoal, sampleUser.getActivityLevel(), nutrition));

        // Priority badges & symbols
        assertEquals("🔴", RecommendationPriority.HIGH.getSymbol());
        assertEquals("🟡", RecommendationPriority.RECOMMENDED.getSymbol());
        assertEquals("🔵", RecommendationPriority.SUPPORTING.getSymbol());
        assertEquals("HIGH PRIORITY", RecommendationPriority.HIGH.getLabel());
        assertTrue(RecommendationPriority.HIGH.getBadge().contains("HIGH PRIORITY"));
    }

    @Test
    public void testSkinHealthLegacyAndCurrentGoalCodes() {
        RecommendationPriorityResolver resolver = new RecommendationPriorityResolver();
        Goal legacySkinGoal = new Goal(4L, "IMPROVE_SKIN_HEALTH", "Improve Skin Health", "Legacy code", true);
        RecommendationCategory hydration = new RecommendationCategory(4L, "Hydration", "Water intake", null, 4);

        // Both SKIN_HEALTH and legacy IMPROVE_SKIN_HEALTH should map identically
        assertEquals(RecommendationPriority.HIGH, resolver.resolve(skinGoal, sampleUser.getActivityLevel(), hydration));
        assertEquals(RecommendationPriority.HIGH, resolver.resolve(legacySkinGoal, sampleUser.getActivityLevel(), hydration));
    }

    @Test
    public void testMatchScoreBreakdownFormula() {
        // Complete profile (5/5 fields filled) -> 100 * 40% = 40.0 points
        // Goal Alignment (Build Muscle with BMI 23.67) -> 95.8 * 40% = 38.32 points
        // Activity synergy (Moderately active for Build Muscle) -> 90.0 * 20% = 18.0 points
        // Total = 40.0 + 38.32 + 18.0 = 96.32 -> 96 points
        MatchScoreBreakdown score = MatchScoreBreakdown.compute(sampleUser, muscleGoal, true, false);

        assertEquals(40.0, score.getProfileCompletenessContribution(), 0.01);
        assertEquals(38.32, score.getGoalAlignmentContribution(), 0.01);
        assertEquals(18.0, score.getActivitySynergyContribution(), 0.01);
        assertEquals(96, score.getTotalScore());

        // Test progress bar generation
        String bar = score.getProgressBar();
        assertNotNull(bar);
        assertTrue(bar.contains("96%"));
        assertTrue(bar.startsWith("[") && bar.contains("]"));
        assertTrue(bar.contains("███████████████████░"));

        // Activity synergy with activity-specific catalog rec (+2 alignment, +3 synergy)
        MatchScoreBreakdown scoreWithCatalogBonus = MatchScoreBreakdown.compute(sampleUser, muscleGoal, true, true);
        assertEquals(97.8, scoreWithCatalogBonus.getGoalAlignment(), 0.01);
        assertEquals(93.0, scoreWithCatalogBonus.getActivitySynergy(), 0.01);
        assertEquals(98, scoreWithCatalogBonus.getTotalScore());
    }

    @Test
    public void testMatchScoreRuleBasedAlignmentForVariousProfiles() {
        Goal gainGoal = new Goal(10L, "GAIN_WEIGHT", "Gain Weight", "Healthy caloric surplus", true);
        Goal loseGoal = new Goal(11L, "LOSE_WEIGHT", "Lose Weight", "Sustainable caloric deficit", true);

        // Case 1: 20 years, 160 cm, 30 kg -> Gain Weight (Severely underweight-like, BMI ~11.7)
        User u30kg = new User();
        u30kg.setAge(20);
        u30kg.setGender(Gender.FEMALE);
        u30kg.setHeightCm(160.0);
        u30kg.setWeightKg(30.0);
        u30kg.setActivityLevel(ActivityLevel.SEDENTARY);

        MatchScoreBreakdown scoreGain30 = MatchScoreBreakdown.compute(u30kg, gainGoal, true, false);
        // Gain weight alignment should be very high (>= 95%) for underweight context
        assertTrue(scoreGain30.getGoalAlignment() >= 95.0, "Gain Weight alignment for 30kg/160cm should be >= 95%");
        assertTrue(scoreGain30.getTotalScore() >= 85, "Total score for 30kg gain weight should be high");
        assertFalse(scoreGain30.hasMismatchWarning(), "Underweight gain weight should not trigger mismatch warning");

        // Case 2: 20 years, 160 cm, 80 kg -> Gain Weight (Higher-weight, BMI ~31.25)
        User u80kg = new User();
        u80kg.setAge(20);
        u80kg.setGender(Gender.FEMALE);
        u80kg.setHeightCm(160.0);
        u80kg.setWeightKg(80.0);
        u80kg.setActivityLevel(ActivityLevel.SEDENTARY);

        MatchScoreBreakdown scoreGain80 = MatchScoreBreakdown.compute(u80kg, gainGoal, true, false);
        // Gain weight alignment should be low (< 20%) for higher-weight context
        assertTrue(scoreGain80.getGoalAlignment() <= 20.0, "Gain Weight alignment for 80kg/160cm should be <= 20%");
        assertTrue(scoreGain80.getTotalScore() <= 55, "Total score for 80kg gain weight should be low");
        assertTrue(scoreGain80.hasMismatchWarning(), "80kg gain weight must trigger profile-goal mismatch warning");

        // Case 3: 20 years, 165 cm, 100 kg -> Gain Weight (High obesity context, BMI ~36.7)
        User u100kg = new User();
        u100kg.setAge(20);
        u100kg.setGender(Gender.MALE);
        u100kg.setHeightCm(165.0);
        u100kg.setWeightKg(100.0);
        u100kg.setActivityLevel(ActivityLevel.SEDENTARY);

        MatchScoreBreakdown scoreGain100 = MatchScoreBreakdown.compute(u100kg, gainGoal, true, false);
        // User with 100kg / 165cm / Gain Weight must have very low alignment and score <= 52% (not 70%)
        assertTrue(scoreGain100.getGoalAlignment() <= 10.0, "Gain Weight alignment for 100kg/165cm should be <= 10%");
        assertTrue(scoreGain100.getTotalScore() <= 52, "Total score for 100kg gain weight should be <= 52% (much lower than 70%)");
        assertTrue(scoreGain100.hasMismatchWarning(), "100kg gain weight must trigger profile-goal mismatch warning");
        assertTrue(scoreGain100.getMismatchWarning().contains("higher-weight"), "Warning must explain higher-weight range");
        assertTrue(scoreGain100.getMismatchWarning().contains("Lose Weight") || scoreGain100.getMismatchWarning().contains("Improve Fitness"),
                "Warning must suggest healthier alternatives");

        // Case 4: 20 years, 160 cm, 50 kg -> Gain Weight (Normal/lean, BMI ~19.5)
        User u50kg = new User();
        u50kg.setAge(20);
        u50kg.setGender(Gender.FEMALE);
        u50kg.setHeightCm(160.0);
        u50kg.setWeightKg(50.0);
        u50kg.setActivityLevel(ActivityLevel.SEDENTARY);

        MatchScoreBreakdown scoreGain50 = MatchScoreBreakdown.compute(u50kg, gainGoal, true, false);
        // Normal/lean should be between underweight and higher-weight
        assertTrue(scoreGain50.getGoalAlignment() > scoreGain80.getGoalAlignment(),
                "50kg gain alignment should be higher than 80kg");
        assertTrue(scoreGain50.getGoalAlignment() < scoreGain30.getGoalAlignment(),
                "50kg gain alignment should be lower than 30kg");
        assertFalse(scoreGain50.hasMismatchWarning(), "Lean/normal gain weight should not trigger mismatch warning");

        // Case 5: Reverse context with Lose Weight
        // 80kg and 100kg with Lose Weight should have high alignment, 30kg with Lose Weight should have severe mismatch
        MatchScoreBreakdown scoreLose100 = MatchScoreBreakdown.compute(u100kg, loseGoal, true, false);
        MatchScoreBreakdown scoreLose80 = MatchScoreBreakdown.compute(u80kg, loseGoal, true, false);
        MatchScoreBreakdown scoreLose30 = MatchScoreBreakdown.compute(u30kg, loseGoal, true, false);

        assertTrue(scoreLose100.getGoalAlignment() >= 95.0, "Lose Weight alignment for 100kg should be >= 95%");
        assertFalse(scoreLose100.hasMismatchWarning(), "100kg lose weight should not trigger mismatch warning");

        assertTrue(scoreLose80.getGoalAlignment() >= 90.0, "Lose Weight alignment for 80kg should be >= 90%");
        assertFalse(scoreLose80.hasMismatchWarning(), "80kg lose weight should not trigger mismatch warning");

        assertTrue(scoreLose30.getGoalAlignment() <= 10.0, "Lose Weight alignment for 30kg should be <= 10%");
        assertTrue(scoreLose30.hasMismatchWarning(), "30kg lose weight must trigger mismatch warning");
        assertTrue(scoreLose30.getMismatchWarning().contains("lower-weight"), "Warning must explain lower-weight range");
    }

    @Test
    public void testGoalCompatibilityStatus() {
        Goal gainGoal = new Goal(10L, "GAIN_WEIGHT", "Gain Weight", "Calorie surplus", true);
        Goal loseGoal = new Goal(11L, "LOSE_WEIGHT", "Lose Weight", "Calorie deficit", true);

        // 18 yrs, 160 cm, 90 kg, Gain Weight -> WARNING
        User u90kg = new User();
        u90kg.setAge(18);
        u90kg.setGender(Gender.MALE);
        u90kg.setHeightCm(160.0);
        u90kg.setWeightKg(90.0);
        u90kg.setActivityLevel(ActivityLevel.SEDENTARY);

        GoalCompatibilityStatus status90 = GoalCompatibilityStatus.compute(u90kg, gainGoal);
        assertEquals(GoalCompatibilityStatus.Level.CONFLICT, status90.getLevel());
        assertTrue(status90.isWarning());
        assertEquals("! GOAL MAY CONFLICT WITH PROFILE", status90.getLevel().getTitle());
        // Verify card rendering
        List<Line> card = status90.renderCard(74);
        assertNotNull(card);
        assertTrue(card.size() >= 8);
        assertTrue(card.get(0).text().contains("┌"));
        assertTrue(card.get(card.size() - 1).text().contains("└"));
        for (Line line : card) {
            assertEquals(72, Theme.width(line.text()), "Every card line must be exactly width 72: " + line.text());
        }

        // 20 yrs, 160 cm, 50 kg, Gain Weight -> ALIGNED
        User u50kg = new User();
        u50kg.setAge(20);
        u50kg.setGender(Gender.FEMALE);
        u50kg.setHeightCm(160.0);
        u50kg.setWeightKg(50.0);
        u50kg.setActivityLevel(ActivityLevel.SEDENTARY);

        GoalCompatibilityStatus status50 = GoalCompatibilityStatus.compute(u50kg, gainGoal);
        assertEquals(GoalCompatibilityStatus.Level.ALIGNED, status50.getLevel());
        assertFalse(status50.isWarning());
        assertEquals("✓ GOAL ALIGNED", status50.getLevel().getTitle());
        assertTrue(status50.getRecommendations().stream().anyMatch(r -> r.contains("gradual weight gain")));

        // 18 yrs, 160 cm, 90 kg, Lose Weight -> ALIGNED
        GoalCompatibilityStatus statusLose90 = GoalCompatibilityStatus.compute(u90kg, loseGoal);
        assertEquals(GoalCompatibilityStatus.Level.ALIGNED, statusLose90.getLevel());
        assertFalse(statusLose90.isWarning());
        assertEquals("✓ GOAL ALIGNED", statusLose90.getLevel().getTitle());
    }

    @Test
    public void testUnderweightProfileSelectingLoseWeightReceivesConflictAndAlternativeSuggestions() {
        // Example: user with BMI 17.3 selecting Lose Weight
        // 170 cm, 50.0 kg -> BMI = 50 / (1.7 * 1.7) = 17.30
        User u17_3 = new User();
        u17_3.setAge(22);
        u17_3.setGender(Gender.FEMALE);
        u17_3.setHeightCm(170.0);
        u17_3.setWeightKg(50.0);
        u17_3.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);

        Goal loseGoal = new Goal(11L, "LOSE_WEIGHT", "Lose Weight", "Calorie deficit", true);
        GoalCompatibilityStatus status = GoalCompatibilityStatus.compute(u17_3, loseGoal);

        // Status must be CONFLICT
        assertEquals(GoalCompatibilityStatus.Level.CONFLICT, status.getLevel());
        assertTrue(status.isWarning());
        assertEquals("! GOAL MAY CONFLICT WITH PROFILE", status.getLevel().getTitle());

        // Message must explain why goal does not align with profile
        String msg = status.getStatusMessage();
        assertTrue(msg.contains("underweight") || msg.contains("17.3"), "Status message must explain underweight context: " + msg);

        // Recommendations must explain why and suggest alternatives
        List<String> recs = status.getRecommendations();
        assertTrue(recs.stream().anyMatch(r -> r.contains("Weight loss not recommended") || r.contains("underweight")),
                "Must explain why weight loss is not recommended: " + recs);
        assertTrue(recs.stream().anyMatch(r -> r.contains("Gain Weight") || r.contains("Build Muscle") || r.contains("Improve Fitness")),
                "Must suggest alternative goals: " + recs);

        // Verify card rendering borders
        List<Line> card = status.renderCard(74);
        assertNotNull(card);
        for (Line line : card) {
            assertEquals(72, Theme.width(line.text()), "Card border alignment must be exact 72: " + line.text());
        }
    }

    @Test
    public void testGoalRequiresCautionForLowerNormalBmi() {
        // User with lower-normal BMI 19.0 selecting Lose Weight
        // 180 cm, 61.6 kg -> BMI = 61.6 / (1.8 * 1.8) = 19.01
        User u19 = new User();
        u19.setAge(25);
        u19.setGender(Gender.MALE);
        u19.setHeightCm(180.0);
        u19.setWeightKg(61.6);
        u19.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        Goal loseGoal = new Goal(11L, "LOSE_WEIGHT", "Lose Weight", "Calorie deficit", true);
        GoalCompatibilityStatus status = GoalCompatibilityStatus.compute(u19, loseGoal);

        assertEquals(GoalCompatibilityStatus.Level.CAUTION, status.getLevel());
        assertTrue(status.isWarning());
        assertEquals("⚠ GOAL REQUIRES CAUTION", status.getLevel().getTitle());
        assertTrue(status.getStatusMessage().contains("19.0") || status.getStatusMessage().contains("underweight threshold"));
    }

    @Test
    public void testHealthyProfileSelectingWeightGoalsDoesNotShowWarningSimplyForGoal() {
        // User with normal BMI 22.0
        // 175 cm, 67.4 kg -> BMI = 67.4 / (1.75 * 1.75) = 22.01
        User u22 = new User();
        u22.setAge(28);
        u22.setGender(Gender.MALE);
        u22.setHeightCm(175.0);
        u22.setWeightKg(67.4);
        u22.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        Goal loseGoal = new Goal(11L, "LOSE_WEIGHT", "Lose Weight", "Calorie deficit", true);
        Goal gainGoal = new Goal(10L, "GAIN_WEIGHT", "Gain Weight", "Calorie surplus", true);

        // Neither should show warning simply because it's Lose Weight or Gain Weight
        GoalCompatibilityStatus loseStatus = GoalCompatibilityStatus.compute(u22, loseGoal);
        assertEquals(GoalCompatibilityStatus.Level.ALIGNED, loseStatus.getLevel());
        assertFalse(loseStatus.isWarning());
        assertEquals("✓ GOAL ALIGNED", loseStatus.getLevel().getTitle());

        GoalCompatibilityStatus gainStatus = GoalCompatibilityStatus.compute(u22, gainGoal);
        assertEquals(GoalCompatibilityStatus.Level.ALIGNED, gainStatus.getLevel());
        assertFalse(gainStatus.isWarning());
        assertEquals("✓ GOAL ALIGNED", gainStatus.getLevel().getTitle());
    }

    @Test
    public void testNoTargetAmountAssumedWhenNotProvided() {
        User user = new User();
        user.setAge(25);
        user.setGender(Gender.FEMALE);
        user.setHeightCm(165.0);
        user.setWeightKg(60.0);
        user.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        Goal gainGoal = new Goal(10L, "GAIN_WEIGHT", "Gain Weight", "Calorie surplus", true);
        GoalCompatibilityStatus status = GoalCompatibilityStatus.compute(user, gainGoal);

        for (String rec : status.getRecommendations()) {
            assertFalse(rec.contains("kg/week"), "Must not assume target rate like kg/week: " + rec);
            assertFalse(rec.contains("+0.25"), "Must not assume numeric target: " + rec);
            assertFalse(rec.contains("+0.5"), "Must not assume numeric target: " + rec);
        }
    }

    @Test
    public void testActivitySynergyDifferentiatesAllActivityLevels() {
        // Different activity levels must produce different synergy values
        User user = new User();
        user.setAge(25);
        user.setGender(Gender.MALE);
        user.setHeightCm(175.0);
        user.setWeightKg(70.0);

        user.setActivityLevel(ActivityLevel.SEDENTARY);
        double sedentarySynergy = MatchScoreBreakdown.computeActivitySynergy(user, muscleGoal, false);

        user.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        double lightlyActiveSynergy = MatchScoreBreakdown.computeActivitySynergy(user, muscleGoal, false);

        user.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        double moderatelyActiveSynergy = MatchScoreBreakdown.computeActivitySynergy(user, muscleGoal, false);

        user.setActivityLevel(ActivityLevel.VERY_ACTIVE);
        double veryActiveSynergy = MatchScoreBreakdown.computeActivitySynergy(user, muscleGoal, false);

        // None of these should be equal to each other for Build Muscle
        assertNotEquals(sedentarySynergy, lightlyActiveSynergy);
        assertNotEquals(lightlyActiveSynergy, moderatelyActiveSynergy);
        assertNotEquals(moderatelyActiveSynergy, veryActiveSynergy);

        // Build muscle benefits progressively from higher activity levels
        assertTrue(sedentarySynergy < lightlyActiveSynergy);
        assertTrue(lightlyActiveSynergy < moderatelyActiveSynergy);
        assertTrue(moderatelyActiveSynergy <= veryActiveSynergy);
    }

    @Test
    public void testProfileCompletenessScalesPerField() {
        User emptyUser = new User();
        assertEquals(0.0, MatchScoreBreakdown.computeProfileCompleteness(emptyUser), 0.01);

        emptyUser.setAge(25);
        assertEquals(20.0, MatchScoreBreakdown.computeProfileCompleteness(emptyUser), 0.01);

        emptyUser.setGender(Gender.FEMALE);
        assertEquals(40.0, MatchScoreBreakdown.computeProfileCompleteness(emptyUser), 0.01);

        emptyUser.setHeightCm(165.0);
        assertEquals(60.0, MatchScoreBreakdown.computeProfileCompleteness(emptyUser), 0.01);

        emptyUser.setWeightKg(58.0);
        assertEquals(80.0, MatchScoreBreakdown.computeProfileCompleteness(emptyUser), 0.01);

        emptyUser.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        assertEquals(100.0, MatchScoreBreakdown.computeProfileCompleteness(emptyUser), 0.01);
    }


    @Test
    public void testCalculationTraceReusesExistingCalculators() {
        CalorieService calorieService = new CalorieService();

        MatchScoreBreakdown score = MatchScoreBreakdown.compute(sampleUser, weightGoal, true, true);
        CalculationTrace trace = new CalculationTrace(sampleUser, weightGoal, calorieService, score);

        // Verify formulas match CalorieCalculator
        double expectedBmr = CalorieCalculator.calculateBmr(
                sampleUser.getGender(),
                sampleUser.getWeightKg(),
                sampleUser.getHeightCm(),
                sampleUser.getAge()
        );
        assertEquals(expectedBmr, trace.getBmrResult(), 0.01);

        double expectedTdee = CalorieCalculator.calculateTdee(expectedBmr, sampleUser.getActivityLevel());
        assertEquals(expectedTdee, trace.getTdeeResult(), 0.01);

        CalorieService.CalorieSummary cs = calorieService.calculateFor(sampleUser, weightGoal);
        assertEquals(cs.suggestedTarget, trace.getSuggestedCalorieTarget(), 0.01);

        // Verify Hydration formula (33.0 mL/kg + activity bonus)
        double expectedLiters = HydrationCalculator.suggestedLitersPerDay(
                sampleUser.getWeightKg(),
                sampleUser.getActivityLevel()
        );
        assertEquals(expectedLiters, trace.getHydrationLiters(), 0.01);

        // Verify formula strings are informative
        assertTrue(trace.getBmrFormula().contains("Mifflin-St Jeor") || trace.getBmrFormula().contains("BMR = 10W"));
        assertTrue(trace.getHydrationFormula().contains("33.0 mL/kg"));
        assertEquals(score.getTotalScore(), trace.getMatchScore().getTotalScore());
    }

    @Test
    public void testDailyBlueprintStructure() {
        DailyBlueprint blueprint = new DailyBlueprint(muscleGoal, sampleUser.getActivityLevel());
        blueprint.addMorning("💧", "Hydration", "Drink 500 mL water upon waking");
        blueprint.addMorning("🍎", "Nutrition", "High protein breakfast");
        blueprint.addMidday("🏋", "Exercise", "Progressive overload resistance training");
        blueprint.addEvening("🧘", "Recovery", "Screen curfew and sleep preparation");

        List<DailyBlueprint.BlueprintItem> morning = blueprint.getMorning();
        List<DailyBlueprint.BlueprintItem> midday = blueprint.getMidday();
        List<DailyBlueprint.BlueprintItem> evening = blueprint.getEvening();

        assertEquals(2, morning.size());
        assertEquals(1, midday.size());
        assertEquals(1, evening.size());

        assertEquals("Hydration", morning.get(0).title());
        assertEquals("Nutrition", morning.get(1).title());
        assertEquals("Exercise", midday.get(0).title());
        assertEquals("Recovery", evening.get(0).title());
    }

    @Test
    public void testThemeDisplayWidthPreservesBoxDrawingAndEmojis() {
        // Box drawing characters must be strictly width 1
        String boxChars = "┌┐└┘├┤┬┴┼─│";
        for (int i = 0; i < boxChars.length(); i++) {
            int codePoint = boxChars.codePointAt(i);
            assertEquals(1, Theme.displayWidth(codePoint),
                    "Box character '" + (char) codePoint + "' must have display width 1");
        }

        // Block elements (progress bars) must be strictly width 1
        String blockChars = "█░▒▓";
        for (int i = 0; i < blockChars.length(); i++) {
            int codePoint = blockChars.codePointAt(i);
            assertEquals(1, Theme.displayWidth(codePoint),
                    "Block character '" + (char) codePoint + "' must have display width 1");
        }

        // ASCII characters must be width 1
        assertEquals(1, Theme.displayWidth('A'));
        assertEquals(1, Theme.displayWidth('1'));
        assertEquals(1, Theme.displayWidth(' '));

        // Priority badges and emojis used in LIFEForge must have display width 2
        String[] emojis = { "🔴", "🟡", "🔵", "⭐", "✅", "❌", "➕", "💧", "🍎", "🏃", "🍽", "🧘", "😴", "🎯" };
        for (String emoji : emojis) {
            int displayW = Theme.width(emoji);
            assertEquals(2, displayW, "Emoji " + emoji + " should have display width 2");
        }

        // Standard Unicode warning symbol has width 1 in terminal emulators
        assertEquals(1, Theme.width("⚠"), "Warning sign '⚠' must have display width 1 in standard terminal emulators");
    }

    @Test
    public void testPersonalizedScreensBorderAlignment() {
        int frameWidth = 80;

        // 1. Personalized Analysis Page
        List<Line> analysisBody = new ArrayList<>();
        analysisBody.add(Line.blank());
        analysisBody.add(Line.of(Theme.headingGreen(), "  MATCH SCORE & ENGINE SYNERGY"));
        analysisBody.add(Line.of(Theme.text(), "  Confidence: [██████████████████░░] 92% (Optimal Synergy)"));
        analysisBody.add(Line.blank());
        analysisBody.add(Line.of(Theme.headingCyan(), "  ACTIVE TARGETS"));
        analysisBody.add(Line.of(Theme.text(), "  Goal:       Build Muscle (Hypertrophy)"));
        analysisBody.add(Line.of(Theme.text(), "  Activity:   Moderately Active (3-5 days/wk)"));
        analysisBody.add(Line.of(Theme.text(), "  Hydration:  3.0 L/day"));
        analysisBody.add(Line.blank());

        List<String[]> footer = List.of(
                new String[] { "Enter", "View Plan" },
                new String[] { "T", "Calc Trace" },
                new String[] { "D", "Daily Blueprint" },
                new String[] { "B", "Back" }
        );

        String page = ScreenKit.page("Personalized Analysis", "Rule-based engine assessment", analysisBody, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        // 2. Personalized Plan Page with Priority Badges
        List<Line> planBody = new ArrayList<>();
        planBody.add(Line.blank());
        planBody.add(Line.of(Theme.headingGreen(), "  RECOMMENDED FOCUS AREAS"));
        planBody.add(Line.of(Theme.text(), "  > 🔴 HIGH PRIORITY     Exercise"));
        planBody.add(Line.of(Theme.text(), "    🔴 HIGH PRIORITY     Nutrition"));
        planBody.add(Line.of(Theme.text(), "    🟡 RECOMMENDED       Sleep & Recovery"));
        planBody.add(Line.of(Theme.text(), "    🔵 SUPPORTING        Daily Micro-Habits"));
        planBody.add(Line.blank());

        String planPage = ScreenKit.page("Personalized Plan", "Prioritized lifestyle roadmap", planBody, "", false, footer, frameWidth);
        assertBorderAlignment(planPage, frameWidth);

        // 3. Calculation Trace Page
        List<Line> traceBody = new ArrayList<>();
        traceBody.add(Line.blank());
        traceBody.add(Line.of(Theme.headingPurple(), "  1. BASAL METABOLIC RATE (BMR)"));
        traceBody.add(Line.of(Theme.text(), "     Formula: MALE: BMR = 10W + 6.25H - 5A + 5"));
        traceBody.add(Line.of(Theme.text(), "     Substitution: = 10(75.0) + 6.25(178.0) - 5(28) + 5"));
        traceBody.add(Line.of(Theme.text(), "     Result: 1728 kcal/day"));
        traceBody.add(Line.blank());
        traceBody.add(Line.of(Theme.headingPurple(), "  2. TOTAL DAILY ENERGY EXPENDITURE (TDEE)"));
        traceBody.add(Line.of(Theme.text(), "     Formula: TDEE = BMR × Activity Multiplier"));
        traceBody.add(Line.of(Theme.text(), "     Substitution: = 1728 × 1.550 (MODERATELY_ACTIVE)"));
        traceBody.add(Line.of(Theme.text(), "     Result: 2678 kcal/day"));
        traceBody.add(Line.blank());

        String tracePage = ScreenKit.page("Calculation Trace", "Academic formula transparency", traceBody, "", false, footer, frameWidth);
        assertBorderAlignment(tracePage, frameWidth);

        // 4. Daily Blueprint Page with Rhythm Emojis
        List<Line> blueprintBody = new ArrayList<>();
        blueprintBody.add(Line.blank());
        blueprintBody.add(Line.of(Theme.headingYellow(), "  MORNING RHYTHM"));
        blueprintBody.add(Line.of(Theme.text(), "    💧 Hydration: Drink 500 mL water upon waking"));
        blueprintBody.add(Line.of(Theme.text(), "    🍎 Nutrition: Protein-forward breakfast"));
        blueprintBody.add(Line.blank());
        blueprintBody.add(Line.of(Theme.headingCyan(), "  MIDDAY RHYTHM"));
        blueprintBody.add(Line.of(Theme.text(), "    🏃 Exercise: Progressive overload resistance training"));
        blueprintBody.add(Line.of(Theme.text(), "    🍽 Lunch: Balanced meal with whole foods"));
        blueprintBody.add(Line.blank());
        blueprintBody.add(Line.of(Theme.headingPurple(), "  EVENING RHYTHM"));
        blueprintBody.add(Line.of(Theme.text(), "    🧘 Recovery: Dim lights and limit screens"));
        blueprintBody.add(Line.of(Theme.text(), "    😴 Sleep: 7-9 hours restorative rest"));
        blueprintBody.add(Line.blank());

        String blueprintPage = ScreenKit.page("Daily Blueprint", "Recommended daily rhythm", blueprintBody, "", false, footer, frameWidth);
        assertBorderAlignment(blueprintPage, frameWidth);

        // 5. Goal Compatibility Status Card wrapped in page frame (Warning state)
        User uWarn = new User();
        uWarn.setAge(20);
        uWarn.setGender(Gender.MALE);
        uWarn.setHeightCm(175.0);
        uWarn.setWeightKg(50.0);
        uWarn.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        Goal loseGoal = new Goal(11L, "LOSE_WEIGHT", "Lose Weight", "Calorie deficit", true);
        GoalCompatibilityStatus compatStatus = GoalCompatibilityStatus.compute(uWarn, loseGoal);
        assertTrue(compatStatus.isWarning());

        List<Line> compatBody = new ArrayList<>();
        compatBody.add(Line.blank());
        compatBody.add(Line.of(Theme.headingCyan(), "  GOAL COMPATIBILITY STATUS"));
        compatBody.addAll(compatStatus.renderCard(frameWidth - 4));

        String compatPage = ScreenKit.page("Personalized Analysis", "Understanding your profile and selected goal", compatBody, "", false, footer, frameWidth);
        assertBorderAlignment(compatPage, frameWidth);
    }

    private void assertBorderAlignment(String page, int expectedWidth) {
        assertNotNull(page);
        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        assertTrue(lines[0].startsWith("┌"));
        assertTrue(lines[0].endsWith("┐"));
        assertTrue(lines[lines.length - 1].startsWith("└"));
        assertTrue(lines[lines.length - 1].endsWith("┘"));

        for (int row = 0; row < lines.length; row++) {
            String l = lines[row];
            assertEquals(expectedWidth, Theme.width(l), "Row " + row + " width is not " + expectedWidth + ": " + l);
            assertTrue(l.endsWith("│") || l.endsWith("┐") || l.endsWith("┤") || l.endsWith("┘"),
                    "Row " + row + " right border misaligned: " + l);
        }
    }

    @Test
    public void testProteinTargetPresentationAndNutritionAreas() {
        int frameWidth = 80;

        List<Line> body = new ArrayList<>();
        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  🍎 PROTEIN-FOCUSED BREAKFAST FOR MUSCLE BUILDING"));
        body.add(Line.blank());

        // Suggested Guidance
        body.add(ScreenKit.section("Suggested Guidance"));
        body.addAll(ScreenKit.paragraph("Include a protein-rich food source with each meal.", frameWidth - 2));

        // Examples
        body.add(Line.blank());
        body.add(ScreenKit.section("Examples"));
        body.add(Line.of(Theme.text(), "   • Eggs"));
        body.add(Line.of(Theme.text(), "   • Greek yogurt"));
        body.add(Line.of(Theme.text(), "   • Chicken"));
        body.add(Line.of(Theme.text(), "   • Fish"));
        body.add(Line.of(Theme.text(), "   • Tofu"));

        // Nutrition Areas
        body.add(Line.blank());
        body.add(ScreenKit.section("Nutrition Areas"));
        body.add(Line.of(Theme.headingGreen(), "   • Protein"));
        body.addAll(ScreenKit.paragraph("     Include a protein-rich food source with each meal.", frameWidth - 2));
        body.add(Line.of(Theme.headingGreen(), "   • Vegetables & Fiber"));
        body.addAll(ScreenKit.paragraph("     Emphasize abundant colorful vegetables, leafy greens, and dietary fiber.", frameWidth - 2));
        body.add(Line.of(Theme.headingGreen(), "   • Balanced Carbohydrates"));
        body.addAll(ScreenKit.paragraph("     Pair with complex carbohydrates (oats, whole grains, sweet potatoes) for steady energy.", frameWidth - 2));
        body.add(Line.of(Theme.headingGreen(), "   • Balanced Meals"));
        body.addAll(ScreenKit.paragraph("     Structure wholesome plates with quality protein, vegetables, and balanced portions.", frameWidth - 2));

        List<String[]> footer = List.of(
                new String[] { "Enter", "Save" },
                new String[] { "W", "Why" },
                new String[] { "A", "AI Chat" },
                new String[] { "B", "Back" }
        );

        String page = ScreenKit.page("Recommendation Detail", "Personalized nutrition guidance", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        // Verify content
        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");
        assertTrue(clean.contains("SUGGESTED GUIDANCE"));
        assertTrue(clean.contains("Include a protein-rich food source with each meal."));
        assertTrue(clean.contains("EXAMPLES"));
        assertTrue(clean.contains("Eggs"));
        assertTrue(clean.contains("Greek yogurt"));
        assertTrue(clean.contains("Chicken"));
        assertTrue(clean.contains("Fish"));
        assertTrue(clean.contains("Tofu"));
        assertTrue(clean.contains("NUTRITION AREAS"));
        assertTrue(clean.contains("Vegetables & Fiber"));
        assertTrue(clean.contains("Balanced Carbohydrates"));
        assertTrue(clean.contains("Balanced Meals"));

        // Verify strict removal of rigid protein gram targets
        assertFalse(clean.contains("120 g protein/day"));
        assertFalse(clean.contains("25–35g protein per meal"));
    }

    @Test
    public void testNutritionRecommendationDetailStructureAndNoEstimatedTargets() {
        int frameWidth = 80;
        List<Line> body = new ArrayList<>();
        body.add(Line.of(Theme.headingCyan(), "  🍎 EAT ENOUGH PROTEIN"));

        Recommendation mockRec = new Recommendation(
                1L, 1L, 1L, ActivityLevel.MODERATELY_ACTIVE,
                "Eat More Protein",
                "Adequate protein supports muscle growth and recovery.",
                "Prioritize high-quality protein sources.",
                null, "120 g protein/day",
                "Maintain a balanced diet and choose a variety of nutrient-dense foods."
        );

        LifeForge.renderNutritionRecommendationDetail(body, mockRec, frameWidth);

        // Horizontal Action Menu matching user image
        body.add(Line.blank());
        List<String> menuItems = List.of(
                "💾 Save Recommendation",
                "💡 Why This?",
                "🤖 Chat with AI",
                "⬅️ Back"
        );
        body.addAll(ScreenKit.menuHorizontal(menuItems, 0, frameWidth - 2));

        List<String[]> footer = List.of(
                new String[] { "Left/Right", "Select" },
                new String[] { "Enter", "Open / action" },
                new String[] { "B", "Back" },
                new String[] { "H", "Home" },
                new String[] { "Q", "Quit" }
        );

        String page = ScreenKit.page("Recommendation Detail", "Specific actions calibrated for your profile", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // 1. Required sections are present
        assertTrue(clean.contains("[ DESCRIPTION ]"));
        assertTrue(clean.contains("Adequate protein supports muscle growth and recovery."));
        assertTrue(clean.contains("🍽️ NUTRITION GUIDANCE"));
        assertTrue(clean.contains("🥗 FOOD SOURCES"));
        assertTrue(clean.contains("[ IMPORTANT NOTES ]"));
        assertTrue(clean.contains("Maintain a balanced diet and choose a variety of nutrient-dense foods."));

        // 2. Card 1 guidance
        assertTrue(clean.contains("Daily Energy Guidance"));
        assertTrue(clean.contains("120 g protein/day"));
        assertTrue(clean.contains("Meal Guidance"));
        assertTrue(clean.contains("Include a protein-rich food source with each meal."));

        // 3. Card 2 food sources
        assertTrue(clean.contains("Animal Sources"));
        assertTrue(clean.contains("Chicken, eggs, fish, Greek yogurt"));
        assertTrue(clean.contains("Plant Sources"));
        assertTrue(clean.contains("Tofu, edamame, lentils, legumes"));

        // 4. Boxed Horizontal Action Buttons with spaces matching user drawing
        assertTrue(clean.contains("[ 💾 Save Recommendation ]"));
        assertTrue(clean.contains("[ 💡 Why This? ]"));
        assertTrue(clean.contains("[ 🤖 Chat with AI ]"));
        assertTrue(clean.contains("[ — Back ]"));
        assertTrue(clean.contains(">[ 💾 Save Recommendation ]  [ 💡 Why This? ]  [ 🤖 Chat with AI ]  [ — Back ]"));

        // 5. No sprawling generic nutrition areas or obsolete sections
        assertFalse(clean.contains("[ NUTRITION AREAS ]"));
        assertFalse(clean.contains("Vegetables & Fiber"));
        assertFalse(clean.contains("Balanced Carbohydrates"));
        assertFalse(clean.contains("Balanced Meals"));

        // 6. No duplicate estimated targets
        assertFalse(clean.contains("PERSONALIZED GUIDANCE"));
        assertFalse(clean.contains("BMR"));
        assertFalse(clean.contains("TDEE"));
        assertFalse(clean.contains("Calorie Target"));
        assertFalse(clean.contains("Hydration Target"));

        // 7. Non-tracking decision engine: no diaries, meal logging, or checkboxes
        assertFalse(clean.contains("Meal Log"));
        assertFalse(clean.contains("Food Diary"));
        assertFalse(clean.contains("Barcode"));
        assertFalse(clean.contains("[ ]"));
        assertFalse(clean.contains("[x]"));
        assertFalse(clean.contains("☑"));
        assertFalse(clean.contains("☐"));

        // 8. Zero truncation ellipsis
        assertFalse(clean.contains("..."));
    }

    @Test
    public void testExerciseRecommendation2CardLayoutAndBorderAlignment() {
        int frameWidth = 80;
        int cardWidth = frameWidth - 8; // 72
        int cardInner = cardWidth - 2;   // 70

        List<Line> body = new ArrayList<>();
        body.add(Line.blank());

        // Card 1: 🏃 RECOMMENDED EXERCISE TYPES
        int title1W = Theme.width("🏃 RECOMMENDED EXERCISE TYPES");
        int dash1 = Math.max(0, cardWidth - 5 - title1W);
        body.add(Line.of(Theme.bar(), "  ┌─ 🏃 RECOMMENDED EXERCISE TYPES " + "─".repeat(dash1) + "┐"));
        body.add(Line.of(Theme.plain(), "  │" + " ".repeat(cardInner) + "│"));
        body.add(Line.of(Theme.plain(), "  │" + Theme.padRight("  • " + Theme.padRight("Aerobic Base", 17) + ": Brisk walking, incline treadmill, or cycling", cardInner) + "│"));
        body.add(Line.of(Theme.plain(), "  │" + Theme.padRight("  • " + Theme.padRight("Resistance Focus", 17) + ": Bodyweight squats, push-ups, light dumbbells", cardInner) + "│"));
        body.add(Line.of(Theme.plain(), "  │" + Theme.padRight("  • " + Theme.padRight("Active Mobility", 17) + ": Dynamic stretching & core stabilization", cardInner) + "│"));
        body.add(Line.of(Theme.plain(), "  │" + " ".repeat(cardInner) + "│"));
        body.add(Line.of(Theme.bar(), "  └" + "─".repeat(cardInner) + "┘"));

        body.add(Line.blank());

        // Card 2: ⏱️ TRAINING PARAMETERS
        int title2W = Theme.width("⏱️ TRAINING PARAMETERS");
        int dash2 = Math.max(0, cardWidth - 5 - title2W);
        body.add(Line.of(Theme.bar(), "  ┌─ ⏱️ TRAINING PARAMETERS " + "─".repeat(dash2) + "┐"));
        body.add(Line.of(Theme.plain(), "  │" + " ".repeat(cardInner) + "│"));
        body.add(Line.of(Theme.plain(), "  │" + Theme.padRight("  • " + Theme.padRight("Target Frequency", 17) + ": 3–4 sessions / week", cardInner) + "│"));
        body.add(Line.of(Theme.plain(), "  │" + Theme.padRight("  • " + Theme.padRight("Session Duration", 17) + ": 30–40 minutes / day", cardInner) + "│"));
        body.add(Line.of(Theme.plain(), "  │" + Theme.padRight("  • " + Theme.padRight("Intensity Zone", 17) + ": Moderate (RPE 6–7 / conversational pace)", cardInner) + "│"));
        body.add(Line.of(Theme.plain(), "  │" + " ".repeat(cardInner) + "│"));
        body.add(Line.of(Theme.bar(), "  └" + "─".repeat(cardInner) + "┘"));

        body.add(Line.blank());
        List<String> menuItems = List.of(
                "💾 Save Recommendation",
                "💡 Why This?",
                "🤖 Chat with AI",
                "⬅️ Back"
        );
        body.addAll(ScreenKit.menu(menuItems, 0, frameWidth - 2));

        List<String[]> footer = List.of(
                new String[] { "Up/Down", "Move" },
                new String[] { "Enter", "Select" },
                new String[] { "Esc", "Back" }
        );

        String page = ScreenKit.page("Recommendation Detail", "Specific actions calibrated for your profile", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // 1. Verify Card 1 contents
        assertTrue(clean.contains("RECOMMENDED EXERCISE TYPES"));
        assertTrue(clean.contains("Aerobic Base"));
        assertTrue(clean.contains("Brisk walking, incline treadmill, or cycling"));
        assertTrue(clean.contains("Resistance Focus"));
        assertTrue(clean.contains("Bodyweight squats, push-ups, light dumbbells"));
        assertTrue(clean.contains("Active Mobility"));
        assertTrue(clean.contains("Dynamic stretching & core stabilization"));

        // 2. Verify Card 2 contents
        assertTrue(clean.contains("TRAINING PARAMETERS"));
        assertTrue(clean.contains("Target Frequency"));
        assertTrue(clean.contains("3–4 sessions / week"));
        assertTrue(clean.contains("Session Duration"));
        assertTrue(clean.contains("30–40 minutes / day"));
        assertTrue(clean.contains("Intensity Zone"));
        assertTrue(clean.contains("Moderate (RPE 6–7 / conversational pace)"));

        // 3. Verify no duplicate metrics
        assertFalse(clean.contains("BMR"));
        assertFalse(clean.contains("TDEE"));
        assertFalse(clean.contains("Calorie Target"));
        assertFalse(clean.contains("Hydration Target"));

        // 4. Verify non-tracking scope guardrails (no checkboxes, rep counters, logging)
        assertFalse(clean.contains("[ ]"));
        assertFalse(clean.contains("Checklist"));
        assertFalse(clean.contains("Log workout"));
        assertFalse(clean.contains("reps"));
    }

    @Test
    public void testSleepRecommendation2CardLayoutAndBorderAlignment() {
        int frameWidth = 80;

        List<Line> body = new ArrayList<>();
        body.add(Line.blank());

        // Card 1: 🌙 SLEEP HYGIENE
        List<String> card1Lines = List.of(
                "  • " + Theme.padRight("Light & Screens", 18) + ": Cut blue light and digital screens 45–60 mins prior to bedtime",
                "  • " + Theme.padRight("Sleep Environment", 18) + ": Keep bedroom cool (~18–20°C), dark, and quiet",
                "  • " + Theme.padRight("Wind-Down Routine", 18) + ": 15–30 mins low-stimulation habit (reading or breathwork)"
        );
        LifeForge.renderBoxCard(body, "🌙 SLEEP HYGIENE", card1Lines, frameWidth);

        body.add(Line.blank());

        // Card 2: 💪 RECOVERY & REST
        List<String> card2Lines = List.of(
                "  • " + Theme.padRight("Sleep Schedule", 18) + ": Target 7–9 hours / night with consistent wake times",
                "  • " + Theme.padRight("Training Recovery", 18) + ": Balance active training with structured rest intervals",
                "  • " + Theme.padRight("Evening Recovery", 18) + ": Taper fluid intake 90 mins before bed and avoid stimulants"
        );
        LifeForge.renderBoxCard(body, "💪 RECOVERY & REST", card2Lines, frameWidth);

        body.add(Line.blank());
        List<String> menuItems = List.of(
                "💾 Save Recommendation",
                "💡 Why This?",
                "🤖 Chat with AI",
                "⬅️ Back"
        );
        body.addAll(ScreenKit.menu(menuItems, 0, frameWidth - 2));

        List<String[]> footer = List.of(
                new String[] { "Up/Down", "Move" },
                new String[] { "Enter", "Select" },
                new String[] { "Esc", "Back" }
        );

        String page = ScreenKit.page("Recommendation Detail", "Specific actions calibrated for your profile", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // 1. Verify Card 1 contents & wrapped phrases (no truncation)
        assertTrue(clean.contains("SLEEP HYGIENE"));
        assertTrue(clean.contains("Light & Screens"));
        assertTrue(clean.contains("Cut blue light and digital screens"));
        assertTrue(clean.contains("Sleep Environment"));
        assertTrue(clean.contains("Keep bedroom cool"));
        assertTrue(clean.contains("Wind-Down Routine"));
        assertTrue(clean.contains("(reading or breathwork)"));

        // 2. Verify Card 2 contents
        assertTrue(clean.contains("RECOVERY & REST"));
        assertTrue(clean.contains("Sleep Schedule"));
        assertTrue(clean.contains("7–9 hours / night"));
        assertTrue(clean.contains("Training Recovery"));
        assertTrue(clean.contains("Evening Recovery"));

        // 3. Verify no duplicate metrics
        assertFalse(clean.contains("BMR"));
        assertFalse(clean.contains("TDEE"));
        assertFalse(clean.contains("Calorie Target"));

        // 4. Verify non-tracking scope guardrails
        assertFalse(clean.contains("sleep timer"));
        assertFalse(clean.contains("alarm trigger"));
        assertFalse(clean.contains("log sleep"));

        // 5. Verify no truncation ellipsis inside cards
        assertFalse(clean.contains("..."));
    }

    @Test
    public void testHydrationRecommendation2CardLayoutAndBorderAlignment() {
        int frameWidth = 80;

        List<Line> body = new ArrayList<>();
        body.add(Line.blank());

        // Card 1: ⏱️ HYDRATION TIMING PROTOCOL
        List<String> card1Lines = List.of(
                "  • " + Theme.padRight("Morning Kickstart", 18) + ": 500 mL upon waking to rehydrate cellular systems",
                "  • " + Theme.padRight("Daytime Cadence", 18) + ": 250–300 mL per waking hour during peak activity",
                "  • " + Theme.padRight("Evening Taper", 18) + ": Reduce large fluid boluses 90 mins prior to bed"
        );
        LifeForge.renderBoxCard(body, "⏱️ HYDRATION TIMING PROTOCOL", card1Lines, frameWidth);

        body.add(Line.blank());

        // Card 2: 📊 INTAKE & ELECTROLYTE PARAMETERS
        List<String> card2Lines = List.of(
                "  • " + Theme.padRight("Target Daily Volume", 22) + ": 2.5–3.0 L/day",
                "  • " + Theme.padRight("Activity Adjustment", 22) + ": +350–500 mL per 30 mins of moderate physical exertion",
                "  • " + Theme.padRight("Electrolyte Balance", 22) + ": Maintain sodium/potassium balance during heat or activity"
        );
        LifeForge.renderBoxCard(body, "📊 INTAKE & ELECTROLYTE PARAMETERS", card2Lines, frameWidth);

        body.add(Line.blank());
        List<String> menuItems = List.of(
                "💾 Save Recommendation",
                "💡 Why This?",
                "🤖 Chat with AI",
                "⬅️ Back"
        );
        body.addAll(ScreenKit.menu(menuItems, 0, frameWidth - 2));

        List<String[]> footer = List.of(
                new String[] { "Up/Down", "Move" },
                new String[] { "Enter", "Select" },
                new String[] { "Esc", "Back" }
        );

        String page = ScreenKit.page("Recommendation Detail", "Specific actions calibrated for your profile", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // 1. Verify Card 1 contents & wrapped phrases (no truncation)
        assertTrue(clean.contains("HYDRATION TIMING PROTOCOL"));
        assertTrue(clean.contains("Morning Kickstart"));
        assertTrue(clean.contains("rehydrate cellular systems"));
        assertTrue(clean.contains("Daytime Cadence"));
        assertTrue(clean.contains("Evening Taper"));
        assertTrue(clean.contains("prior to bed"));

        // 2. Verify Card 2 contents & wrapped phrases
        assertTrue(clean.contains("INTAKE & ELECTROLYTE PARAMETERS"));
        assertTrue(clean.contains("Target Daily Volume"));
        assertTrue(clean.contains("Activity Adjustment"));
        assertTrue(clean.contains("moderate physical exertion"));
        assertTrue(clean.contains("Electrolyte Balance"));

        // 3. Verify no duplicate metrics
        assertFalse(clean.contains("BMR"));
        assertFalse(clean.contains("TDEE"));
        assertFalse(clean.contains("Calorie Target"));

        // 4. Verify non-tracking scope guardrails
        assertFalse(clean.contains("water intake logging"));
        assertFalse(clean.contains("cup counter"));
        assertFalse(clean.contains("log water"));

        // 5. Verify no truncation ellipsis inside cards
        assertFalse(clean.contains("..."));
    }

    @Test
    public void testRenderBoxCardWordWrappingAndLabelIndentation() {
        int cardWidth = 72; // 80 - 8

        // Test 1: Sleep - Light Control wrapping & label indentation
        String sleepLight = "  • " + Theme.padRight("Light Control", 16) + ": Cut blue light & digital screens 45–60 mins prior to bed";
        List<String> wrappedSleepLight = LifeForge.wrapCardLine(sleepLight, cardWidth);
        assertEquals(2, wrappedSleepLight.size());
        assertEquals("  • Light Control   : Cut blue light & digital screens 45–60 mins", wrappedSleepLight.get(0));
        assertEquals("                      prior to bed", wrappedSleepLight.get(1));
        // Indentation aligns right below value: exactly 22 spaces
        assertTrue(wrappedSleepLight.get(1).startsWith(" ".repeat(22)));

        // Test 2: Sleep - Wind-Down Habit with parenthesized expression
        String sleepWindDown = "  • " + Theme.padRight("Wind-Down Habit", 16) + ": 10–15 mins low-stimulation routine (reading or breathwork)";
        List<String> wrappedWindDown = LifeForge.wrapCardLine(sleepWindDown, cardWidth);
        assertEquals(2, wrappedWindDown.size());
        assertEquals("  • Wind-Down Habit : 10–15 mins low-stimulation routine", wrappedWindDown.get(0));
        assertEquals("                      (reading or breathwork)", wrappedWindDown.get(1));
        assertTrue(wrappedWindDown.get(1).startsWith(" ".repeat(22)));

        // Test 3: Hydration - Morning Kickstart
        String hydraMorning = "  • " + Theme.padRight("Morning Kickstart", 18) + ": 500 mL upon waking to rehydrate cellular systems";
        List<String> wrappedMorning = LifeForge.wrapCardLine(hydraMorning, cardWidth);
        assertEquals(2, wrappedMorning.size());
        assertEquals("  • Morning Kickstart : 500 mL upon waking to", wrappedMorning.get(0));
        assertEquals("                        rehydrate cellular systems", wrappedMorning.get(1));
        assertTrue(wrappedMorning.get(1).startsWith(" ".repeat(24)));

        // Test 4: Hydration - Activity Adjustment
        String hydraActivity = "  • " + Theme.padRight("Activity Adjustment", 22) + ": +350–500 mL per 30 mins of moderate physical exertion";
        List<String> wrappedActivity = LifeForge.wrapCardLine(hydraActivity, cardWidth);
        assertEquals(2, wrappedActivity.size());
        assertEquals("  • Activity Adjustment   : +350–500 mL per 30 mins of", wrappedActivity.get(0));
        assertEquals("                            moderate physical exertion", wrappedActivity.get(1));
        assertTrue(wrappedActivity.get(1).startsWith(" ".repeat(28)));

        // Test 5: Verify rendered rows inside a card have exact width and border alignment
        List<Line> body = new ArrayList<>();
        LifeForge.renderBoxCard(body, "🌙 SLEEP HYGIENE PROTOCOL", List.of(sleepLight, sleepWindDown), 80);
        for (Line l : body) {
            String plainText = l.text().replaceAll("\u001B\\[[;\\d]*m", "");
            assertEquals(74, Theme.width(plainText), "Line width must be 74: " + plainText);
            assertTrue(plainText.startsWith("  ┌") || plainText.startsWith("  │") || plainText.startsWith("  └"));
            assertTrue(plainText.endsWith("┐") || plainText.endsWith("│") || plainText.endsWith("┘"));
            assertFalse(plainText.contains("..."));
        }
    }

    @Test
    public void testHabitsRecommendation2CardLayoutAndBorderAlignment() {
        int frameWidth = 80;

        Recommendation r = new Recommendation();
        r.setId(40L);
        r.setGoalId(1L);
        r.setCategoryId(4L);
        r.setActivityLevel(ActivityLevel.SEDENTARY);
        r.setTitle("Daily Micro-Habits for Weight Loss");
        r.setDescription("Small consistent habits that compound over time to sustain active metabolic health.");
        r.setImportantNotes("These are suggestions to try, not a checklist to complete daily.");

        List<Line> body = new ArrayList<>();
        body.add(Line.blank());

        // Header
        String title = r.getTitle().trim();
        String displayTitle = title.startsWith("🌱") ? title : "🌱 " + title;
        body.add(Line.of(Theme.headingCyan(), "  " + displayTitle.toUpperCase(Locale.ROOT)));

        // Description
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  [ DESCRIPTION ]"));
        body.addAll(ScreenKit.paragraph(r.getDescription(), frameWidth - 2));

        body.add(Line.blank());

        // Card 1 (Top Box): 🌱 HIGH-LEVERAGE DAILY HABITS
        List<String> card1Lines = List.of(
                "  • " + Theme.padRight("Protein Anchor", 19) + ": Pre-portion protein sources at breakfast & lunch",
                "  • " + Theme.padRight("Movement Prep", 19) + ": Stage training apparel & gear the night prior",
                "  • " + Theme.padRight("Posture / Bracing", 19) + ": 2-minute core & posture reset every 2 hours sit"
        );
        LifeForge.renderBoxCard(body, "🌱 HIGH-LEVERAGE DAILY HABITS", card1Lines, frameWidth);

        body.add(Line.blank());

        // Card 2 (Bottom Box): ⚡ HABIT ANCHORING & TIMING
        List<String> card2Lines = List.of(
                "  • " + Theme.padRight("Friction Reduction", 20) + ": Keep hydration bottle visible on workstation",
                "  • " + Theme.padRight("Habit Loop Trigger", 20) + ": Pair post-workout shake directly after training",
                "  • " + Theme.padRight("Recovery Shutdown", 20) + ": Set static digital curfew 45 mins before sleep"
        );
        LifeForge.renderBoxCard(body, "⚡ HABIT ANCHORING & TIMING", card2Lines, frameWidth);

        // Important Notes
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  [ IMPORTANT NOTES ]"));
        body.addAll(ScreenKit.paragraph(r.getImportantNotes(), frameWidth - 2));

        body.add(Line.blank());
        List<String> menuItems = List.of(
                "💾 Save Recommendation",
                "💡 Why This?",
                "🤖 Chat with AI",
                "⬅️ Back"
        );
        body.addAll(ScreenKit.menu(menuItems, 0, frameWidth - 2));

        List<String[]> footer = List.of(
                new String[] { "Up/Down", "Move" },
                new String[] { "Enter", "Select" },
                new String[] { "Esc", "Back" }
        );

        String page = ScreenKit.page("Recommendation Detail", "Specific actions calibrated for your profile", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // 1. Verify Core Context Preserved
        assertTrue(clean.contains("🌱 DAILY MICRO-HABITS FOR WEIGHT LOSS"));
        assertTrue(clean.contains("[ DESCRIPTION ]"));
        assertTrue(clean.contains("Small consistent habits that compound over time"));
        assertTrue(clean.contains("[ IMPORTANT NOTES ]"));
        assertTrue(clean.contains("These are suggestions to try, not a checklist to complete daily."));

        // 2. Verify Card 1 contents
        assertTrue(clean.contains("HIGH-LEVERAGE DAILY HABITS"));
        assertTrue(clean.contains("Protein Anchor"));
        assertTrue(clean.contains("Pre-portion protein sources"));
        assertTrue(clean.contains("breakfast & lunch"));
        assertTrue(clean.contains("Movement Prep"));
        assertTrue(clean.contains("Stage training apparel"));
        assertTrue(clean.contains("night prior"));
        assertTrue(clean.contains("Posture / Bracing"));
        assertTrue(clean.contains("2-minute core"));
        assertTrue(clean.contains("2 hours sit"));

        // 3. Verify Card 2 contents
        assertTrue(clean.contains("HABIT ANCHORING & TIMING"));
        assertTrue(clean.contains("Friction Reduction"));
        assertTrue(clean.contains("Keep hydration bottle"));
        assertTrue(clean.contains("workstation"));
        assertTrue(clean.contains("Habit Loop Trigger"));
        assertTrue(clean.contains("Pair post-workout shake"));
        assertTrue(clean.contains("after training"));
        assertTrue(clean.contains("Recovery Shutdown"));
        assertTrue(clean.contains("Set static digital curfew"));
        assertTrue(clean.contains("before sleep"));

        // 4. Verify removal of out-of-place content
        assertFalse(clean.contains("[ NUTRITION AREAS ]"));
        assertFalse(clean.contains("Vegetables & Fiber"));
        assertFalse(clean.contains("Balanced Carbohydrates"));
        assertFalse(clean.contains("[ PERSONALIZED GUIDANCE ]"));
        assertFalse(clean.contains("BMR"));
        assertFalse(clean.contains("TDEE"));
        assertFalse(clean.contains("Calorie Target"));

        // 5. Verify non-tracking scope guardrails
        assertFalse(clean.contains("[ ]"));
        assertFalse(clean.contains("[x]"));
        assertFalse(clean.contains("streak"));
        assertFalse(clean.contains("daily check-in"));
        assertFalse(clean.contains("check-in"));

        // 6. Verify no truncation ellipsis inside cards
        assertFalse(clean.contains("..."));
    }

    @Test
    public void testCompleteMasterRoutineSynthesis() throws SQLException {
        RecommendationCategory masterCat = new RecommendationCategory(5L, "Complete Master Routine", "Combined daily routine", null, 5);
        RecommendationCategory nutritionCat = new RecommendationCategory(1L, "Nutrition", "Dietary guidance", null, 1);
        RecommendationCategory exerciseCat = new RecommendationCategory(2L, "Exercise", "Workouts", null, 2);
        RecommendationCategory sleepCat = new RecommendationCategory(3L, "Sleep & Recovery", "Rest", null, 3);
        RecommendationCategory habitsCat = new RecommendationCategory(4L, "Daily Micro-Habits", "Micro habits", null, 4);
        RecommendationCategory hydrationCat = new RecommendationCategory(7L, "Hydration Guidance", "Water intake", null, 7);

        RecommendationCategoryDao fakeCatDao = new RecommendationCategoryDao() {
            @Override
            public List<RecommendationCategory> findTopLevel() {
                return List.of(nutritionCat, exerciseCat, sleepCat, habitsCat, masterCat, hydrationCat);
            }

            @Override
            public Optional<RecommendationCategory> findById(Long id) {
                if (id == 5L) return Optional.of(masterCat);
                if (id == 1L) return Optional.of(nutritionCat);
                if (id == 2L) return Optional.of(exerciseCat);
                if (id == 3L) return Optional.of(sleepCat);
                if (id == 4L) return Optional.of(habitsCat);
                if (id == 7L) return Optional.of(hydrationCat);
                return Optional.empty();
            }
        };

        RecommendationDao fakeRecDao = new RecommendationDao() {
            @Override
            public Optional<Recommendation> findBestMatch(Long goalId, Long categoryId, ActivityLevel activityLevel) {
                if (categoryId == 1L) {
                    return Optional.of(new Recommendation(101L, goalId, 1L, activityLevel,
                            "Nutrition Focus", "Eating plan", "Prioritize protein and fiber; avoid refined sugars.",
                            "400-500 kcal", "Eggs + oatmeal", "Portion appropriately"));
                } else if (categoryId == 2L) {
                    return Optional.of(new Recommendation(102L, goalId, 2L, activityLevel,
                            "Strength & Cardio Routine", "Physical training", "Execute structured progressive resistance training 4x/week.",
                            "4 sessions/wk", "Compound lifts", "Form first"));
                } else if (categoryId == 3L) {
                    return Optional.of(new Recommendation(103L, goalId, 3L, activityLevel,
                            "Sleep & Recovery Hygiene", "Rest and sleep", "Aim for 7-9 hours of consistent sleep; avoid screens 30-60 min before bed.",
                            "7-9 hrs/night", "Dim lighting", "No late caffeine"));
                } else if (categoryId == 4L) {
                    return Optional.of(new Recommendation(104L, goalId, 4L, activityLevel,
                            "Daily Micro-Habits", "Micro habits", "Drink a glass of water before meals; take a 10-minute post-dinner walk.",
                            null, "Morning water", "Consistency matters"));
                } else if (categoryId == 7L) {
                    return Optional.of(new Recommendation(105L, goalId, 7L, activityLevel,
                            "Hydration Guidance", "Water intake", "Sip water steadily throughout the day rather than large amounts at once.",
                            "2.5-3.0 L/day", "Reusable water bottle", "Adjust for climate"));
                }
                return Optional.empty();
            }
        };

        RecommendationEngine engine = new RecommendationEngine(fakeRecDao);
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        RuleBasedExplanationService explanationService = new RuleBasedExplanationService();

        RecommendationService service = new RecommendationService(
                engine,
                fakeCatDao,
                fakeRecDao,
                calorieService,
                hydrationService,
                explanationService,
                explanationService
        );

        // 1. Verify category detection
        assertTrue(service.isMasterRoutineCategory(masterCat));
        assertFalse(service.isMasterRoutineCategory(nutritionCat));
        assertFalse(service.isMasterRoutineCategory(exerciseCat));

        // 2. Synthesize Master Routine
        RecommendationService.RecommendationResult result = service.buildMasterRoutineResult(sampleUser, muscleGoal, masterCat);
        assertNotNull(result);
        assertNotNull(result.recommendation);

        Recommendation rec = result.recommendation;
        assertNotNull(rec.getId(), "Complete Master Routine must have a valid ID so it can be saved");
        assertTrue(rec.getTitle().contains("Complete Master Routine"));
        assertTrue(rec.getTitle().contains("Build Muscle"));

        // 3. Verify that all 5 pillars are combined in recommended actions
        String actions = rec.getRecommendedActions();
        assertTrue(actions.contains("🍎 Nutrition:"));
        assertTrue(actions.contains("Prioritize protein and fiber; avoid refined sugars."));

        assertTrue(actions.contains("🏋 Exercise:"));
        assertTrue(actions.contains("Execute structured progressive resistance training 4x/week."));

        assertTrue(actions.contains("💧 Hydration:"));
        assertTrue(actions.contains("Sip water steadily throughout the day rather than large amounts at once."));

        assertTrue(actions.contains("😴 Sleep & Recovery:"));
        assertTrue(actions.contains("Aim for 7-9 hours of consistent sleep; avoid screens 30-60 min before bed."));

        assertTrue(actions.contains("🌱 Daily Micro-Habits:"));
        assertTrue(actions.contains("Drink a glass of water before meals; take a 10-minute post-dinner walk."));

        // 4. Verify suggested targets include calorie and hydration targets
        assertNotNull(rec.getSuggestedTarget());
        assertTrue(rec.getSuggestedTarget().contains("Hydration:"));
        assertTrue(rec.getSuggestedTarget().contains("Exercise:"));
        assertTrue(rec.getSuggestedTarget().contains("Sleep:"));

        // 5. Verify direct lookup interception via getRecommendation(user, goal, 5L)
        Optional<RecommendationService.RecommendationResult> generated = service.getRecommendation(sampleUser, muscleGoal, 5L);
        assertTrue(generated.isPresent());
        assertNotNull(generated.get().recommendation.getId());
        assertTrue(generated.get().recommendation.getRecommendedActions().contains("🍎 Nutrition:"));
        assertTrue(generated.get().recommendation.getRecommendedActions().contains("🏋 Exercise:"));
        assertTrue(generated.get().recommendation.getRecommendedActions().contains("💧 Hydration:"));
        assertTrue(generated.get().recommendation.getRecommendedActions().contains("😴 Sleep & Recovery:"));
        assertTrue(generated.get().recommendation.getRecommendedActions().contains("🌱 Daily Micro-Habits:"));
    }

    @Test
    public void testGlobalAssistantPromptAssemblyAndNumericLockdown() {
        AiExplanationService aiService = new AiExplanationService();
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();

        CalorieService.CalorieSummary cs = calorieService.calculateFor(sampleUser, muscleGoal);
        double hydration = hydrationService.suggestedLitersPerDay(sampleUser);

        List<PersonalizedPlanResult.AreaItem> areas = List.of(
                new PersonalizedPlanResult.AreaItem(
                        new RecommendationCategory(1L, "Nutrition", "Diet", null, 1),
                        "🍎",
                        RecommendationPriority.HIGH,
                        new Recommendation(10L, muscleGoal.getId(), 1L, sampleUser.getActivityLevel(),
                                "Protein-Focused Nutrition", "Diet", "Include protein with every meal.", "Balanced meals", "Eggs, chicken", "Notes")
                ),
                new PersonalizedPlanResult.AreaItem(
                        new RecommendationCategory(2L, "Exercise", "Training", null, 2),
                        "🏋",
                        RecommendationPriority.HIGH,
                        new Recommendation(20L, muscleGoal.getId(), 2L, sampleUser.getActivityLevel(),
                                "Hypertrophy Training", "Lifting", "Train 4 days a week.", "4x/wk", "Barbell", "Notes")
                )
        );
        MatchScoreBreakdown matchScore = MatchScoreBreakdown.compute(sampleUser, muscleGoal, true, false);
        PersonalizedPlanResult plan = new PersonalizedPlanResult(
                muscleGoal,
                sampleUser.getActivityLevel(),
                "Focus on protein and strength",
                matchScore,
                areas
        );

        // 1. Direct verification of deterministic Rule Engine fallback
        AiChatResponse responseFocus = aiService.globalFallback(
                sampleUser, muscleGoal, plan, cs, hydration, true, "What should I focus on first?");
        assertNotNull(responseFocus);
        assertFalse(responseFocus.fromAi);
        assertTrue(responseFocus.text.contains("Nutrition"));
        assertTrue(responseFocus.text.contains("Exercise"));
        assertTrue(responseFocus.text.contains(String.format(java.util.Locale.ROOT, "%.1f L/day", hydration)));

        // Prompt 2: What should I eat?
        AiChatResponse responseEat = aiService.globalFallback(
                sampleUser, muscleGoal, plan, cs, hydration, true, "What should I eat?");
        assertNotNull(responseEat);
        assertFalse(responseEat.fromAi);
        assertTrue(responseEat.text.toLowerCase().contains("protein"));
        assertEquals(1L, responseEat.suggestedCategoryId);
        assertEquals("Nutrition", responseEat.suggestedCategoryName);

        // Prompt 3: What exercise should I do?
        AiChatResponse responseExercise = aiService.globalFallback(
                sampleUser, muscleGoal, plan, cs, hydration, true, "What exercise should I do?");
        assertNotNull(responseExercise);
        assertFalse(responseExercise.fromAi);
        assertEquals(2L, responseExercise.suggestedCategoryId);
        assertEquals("Exercise", responseExercise.suggestedCategoryName);

        // Prompt 5: How much water should I drink?
        AiChatResponse responseWater = aiService.globalFallback(
                sampleUser, muscleGoal, plan, cs, hydration, true, "How much water should I drink?");
        assertNotNull(responseWater);
        assertFalse(responseWater.fromAi);
        assertTrue(responseWater.text.contains(String.format(java.util.Locale.ROOT, "%.1f L/day", hydration)));

        // 2. Verification of chatGlobal routing with AI disabled (offline simulation)
        String prevFlag = System.getProperty("lifeforge.ai.enabled");
        try {
            System.setProperty("lifeforge.ai.enabled", "false");
            assertFalse(aiService.isAvailable());

            AiChatResponse offlineResp = aiService.chatGlobal(
                    sampleUser, muscleGoal, plan, cs, hydration, true, List.of(), "What should I focus on first?");
            assertNotNull(offlineResp);
            assertFalse(offlineResp.fromAi);
            assertTrue(offlineResp.text.contains("Nutrition"));
        } finally {
            if (prevFlag != null) {
                System.setProperty("lifeforge.ai.enabled", prevFlag);
            } else {
                System.clearProperty("lifeforge.ai.enabled");
            }
        }
    }

    @Test
    public void testGlobalAssistantWithoutSelectedGoal() {
        AiExplanationService aiService = new AiExplanationService();
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();

        // When goal is null:
        Goal nullGoal = null;
        PersonalizedPlanResult nullPlan = null;
        CalorieService.CalorieSummary cs = calorieService.calculateFor(sampleUser, nullGoal);
        double hydration = hydrationService.suggestedLitersPerDay(sampleUser);

        // 1. "What should I focus on?" (prompt 1)
        AiChatResponse respFocus = aiService.globalFallback(
                sampleUser, nullGoal, nullPlan, cs, hydration, false, "What should I focus on?");
        assertNotNull(respFocus);
        assertFalse(respFocus.fromAi);
        assertFalse(respFocus.text.contains("Goal:"));
        assertFalse(respFocus.text.contains("Not selected yet"));
        assertFalse(respFocus.text.contains("Choose Goal"));
        assertTrue(respFocus.text.contains("Hydration"));
        assertTrue(respFocus.text.contains("Nutrition"));

        // 2. "What should I eat?" (prompt 2)
        AiChatResponse respEat = aiService.globalFallback(
                sampleUser, nullGoal, nullPlan, cs, hydration, false, "What should I eat?");
        assertNotNull(respEat);
        assertFalse(respEat.fromAi);
        assertFalse(respEat.text.contains("Goal:"));
        assertFalse(respEat.text.contains("Choose Goal"));
        assertTrue(respEat.text.contains("nutrition guidance"));
        assertTrue(respEat.text.contains("protein-rich food source"));

        // 3. "What exercise should I do?" (prompt 3)
        AiChatResponse respExe = aiService.globalFallback(
                sampleUser, nullGoal, nullPlan, cs, hydration, false, "What exercise should I do?");
        assertNotNull(respExe);
        assertFalse(respExe.fromAi);
        assertFalse(respExe.text.contains("Goal:"));
        assertFalse(respExe.text.contains("Choose Goal"));
        assertTrue(respExe.text.contains("exercise guidance"));
        assertTrue(respExe.text.contains(sampleUser.getActivityLevel().name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT)));

        // 4. "How can I improve my sleep?" (prompt 4)
        AiChatResponse respSleep = aiService.globalFallback(
                sampleUser, nullGoal, nullPlan, cs, hydration, false, "How can I improve my sleep?");
        assertNotNull(respSleep);
        assertFalse(respSleep.fromAi);
        assertFalse(respSleep.text.contains("Goal:"));
        assertFalse(respSleep.text.contains("Choose Goal"));
        assertTrue(respSleep.text.contains("sleep and recovery guidance"));
        assertTrue(respSleep.text.contains("7-9 hours"));

        // 5. "How much water should I drink?" (prompt 5)
        AiChatResponse respWater = aiService.globalFallback(
                sampleUser, nullGoal, nullPlan, cs, hydration, false, "How much water should I drink?");
        assertNotNull(respWater);
        assertFalse(respWater.fromAi);
        assertFalse(respWater.text.contains("Goal:"));
        assertTrue(respWater.text.contains(String.format(java.util.Locale.ROOT, "%.1f L/day", hydration)));
        assertTrue(respWater.text.contains("profile"));

        // 6. User asks about goals ("What goal may suit me?") - provides guidance but NEVER mutates or demands goal
        AiChatResponse respGoal = aiService.globalFallback(
                sampleUser, nullGoal, nullPlan, cs, hydration, false, "What goal may suit me?");
        assertNotNull(respGoal);
        assertFalse(respGoal.fromAi);
        assertFalse(respGoal.text.contains("Goal:"));
        assertFalse(respGoal.text.contains("Choose Goal"));
        assertTrue(respGoal.text.contains("guidance on lifestyle directions that may suit you"));
        assertTrue(respGoal.text.contains("BMI"));
        assertTrue(respGoal.text.contains("LIFEForge does not automatically select or change your goal"));

        // 7. Verification with chatGlobal offline routing
        String prevFlag = System.getProperty("lifeforge.ai.enabled");
        try {
            System.setProperty("lifeforge.ai.enabled", "false");
            AiChatResponse offlineResp = aiService.chatGlobal(
                    sampleUser, nullGoal, nullPlan, cs, hydration, false, List.of(), "How can I improve my sleep?");
            assertNotNull(offlineResp);
            assertFalse(offlineResp.fromAi);
            assertFalse(offlineResp.text.contains("Goal:"));
            assertTrue(offlineResp.text.contains("sleep and recovery guidance"));
        } finally {
            if (prevFlag != null) {
                System.setProperty("lifeforge.ai.enabled", prevFlag);
            } else {
                System.clearProperty("lifeforge.ai.enabled");
            }
        }
    }

    @Test
    public void testTwoSeparateAiContextsModeAAndModeB() {
        AiExplanationService aiService = new AiExplanationService();
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();

        Goal loseGoal = new Goal(11L, "LOSE_WEIGHT", "Lose Weight", "Calorie deficit", true);
        RecommendationCategory nutCat = new RecommendationCategory(1L, "Nutrition", "Dietary strategies", null, 1);
        Recommendation balancedNutrition = new Recommendation(
                101L,
                loseGoal.getId(),
                nutCat.getId(),
                ActivityLevel.SEDENTARY,
                "Balanced Nutrition",
                "Caloric moderation",
                "Create a moderate deficit while prioritizing protein and fiber.",
                "1800 kcal/day",
                "Oatmeal with berries and protein powder, vegetable omelet with whole grain toast",
                "Prioritize hydration and avoid late-night processed carbohydrates."
        );

        // =========================================================================
        // Context 1: Dashboard -> AI Assistant (GENERAL / FREE)
        // =========================================================================
        // Even if user has a goal, global assistant never displays the goal or behaves like a goal screen
        CalorieService.CalorieSummary cs = calorieService.calculateFor(sampleUser, loseGoal);
        double hydration = hydrationService.suggestedLitersPerDay(sampleUser);

        AiChatResponse freeFocus = aiService.globalFallback(
                sampleUser, loseGoal, null, cs, hydration, true, "What should I focus on?");
        assertNotNull(freeFocus);
        assertFalse(freeFocus.text.contains("Goal:"));
        assertFalse(freeFocus.text.contains("Not selected yet"));
        assertTrue(freeFocus.text.contains("Hydration"));
        assertTrue(freeFocus.text.contains("Nutrition"));

        // =========================================================================
        // Context 2: Recommendation Detail -> Chat with AI (GOAL + CATEGORY + REC SPECIFIC)
        // =========================================================================
        // When user asks "What should I eat for breakfast?", response is specifically
        // tailored to Lose Weight + Nutrition + Balanced Nutrition
        AiChatResponse recChat = aiService.chatFallback(
                sampleUser, loseGoal, nutCat, balancedNutrition, "What should I eat for breakfast?");
        assertNotNull(recChat);
        assertFalse(recChat.fromAi);
        // Must contain goal name, recommendation title, and category
        assertTrue(recChat.text.contains("Lose Weight"));
        assertTrue(recChat.text.contains("Balanced Nutrition"));
        assertTrue(recChat.text.contains("Nutrition"));
        // Must contain meal/breakfast ideas from the recommendation examples
        assertTrue(recChat.text.contains("Oatmeal") || recChat.text.contains("omelet"));
        assertEquals(nutCat.getId(), recChat.suggestedCategoryId);
        assertEquals("Nutrition", recChat.suggestedCategoryName);

        // Verification with chat() offline routing
        String prevFlag = System.getProperty("lifeforge.ai.enabled");
        try {
            System.setProperty("lifeforge.ai.enabled", "false");
            AiChatResponse offlineRecResp = aiService.chat(
                    sampleUser, loseGoal, nutCat, balancedNutrition, List.of(), "What should I eat for breakfast?");
            assertNotNull(offlineRecResp);
            assertTrue(offlineRecResp.text.contains("Lose Weight"));
            assertTrue(offlineRecResp.text.contains("Balanced Nutrition"));
            assertTrue(offlineRecResp.text.contains("Oatmeal") || offlineRecResp.text.contains("omelet"));
        } finally {
            if (prevFlag != null) {
                System.setProperty("lifeforge.ai.enabled", prevFlag);
            } else {
                System.clearProperty("lifeforge.ai.enabled");
            }
        }
    }

    @Test
    public void testChatWithAiGoalBroadGuidanceForSkinHealth() {
        AiExplanationService aiService = new AiExplanationService();

        RecommendationCategory nutCat = new RecommendationCategory(1L, "Nutrition", "Dietary guidance", null, 1);
        RecommendationCategory hydCat = new RecommendationCategory(2L, "Hydration", "Hydration guidance", null, 2);
        RecommendationCategory habitCat = new RecommendationCategory(3L, "Daily Micro-Habits", "Habits", null, 3);
        RecommendationCategory sleepCat = new RecommendationCategory(4L, "Sleep & Recovery", "Rest", null, 4);

        Recommendation skinNutRec = new Recommendation(
                10L, 2L, 1L, ActivityLevel.MODERATELY_ACTIVE,
                "Nutrition for Healthy Skin",
                "Nourish skin from within with antioxidant-rich foods and hydration.",
                "Eat berries, leafy greens, avocados, fatty fish, and nuts daily.",
                "Incorporate colorful fruits and vegetables into each meal",
                "Blueberries, salmon, walnuts, spinach salad",
                "Reduce processed sugars which can aggravate skin inflammation."
        );

        Recommendation skinHydRec = new Recommendation(
                11L, 2L, 2L, ActivityLevel.MODERATELY_ACTIVE,
                "Consistent Skin Hydration",
                "Maintain optimal cellular hydration for skin barrier health.",
                "Drink water consistently throughout the day.",
                "2.6 L/day",
                "Morning glass upon waking, water bottle by desk",
                "Caffeine can be mildly dehydrating; offset with extra water."
        );

        PersonalizedPlanResult plan = new PersonalizedPlanResult(
                skinGoal,
                sampleUser.getActivityLevel(),
                "Support dermatological wellness and moisture barrier through nutrition, hydration, and restorative habits.",
                MatchScoreBreakdown.compute(sampleUser, skinGoal, true, false),
                List.of(
                        new PersonalizedPlanResult.AreaItem(nutCat, "🍎", RecommendationPriority.HIGH, skinNutRec),
                        new PersonalizedPlanResult.AreaItem(hydCat, "💧", RecommendationPriority.HIGH, skinHydRec)
                )
        );

        // 1. User asks about daily habits when on the Nutrition recommendation
        AiChatResponse habitResp = aiService.chatFallback(
                sampleUser, skinGoal, nutCat, skinNutRec, plan, "What daily habits best support my Improve Skin Health goal?");
        assertNotNull(habitResp);
        assertTrue(habitResp.text.contains("Improve Skin Health"));
        assertTrue(habitResp.text.contains("Gentle cleanse") || habitResp.text.contains("SPF") || habitResp.text.contains("Routine"));

        // 2. User asks about water/hydration when on the Nutrition recommendation
        AiChatResponse waterResp = aiService.chatFallback(
                sampleUser, skinGoal, nutCat, skinNutRec, plan, "How much water should I drink for my Improve Skin Health goal?");
        assertNotNull(waterResp);
        assertTrue(waterResp.text.contains("Hydration"));
        assertTrue(waterResp.text.contains("Improve Skin Health"));
        assertTrue(waterResp.text.contains("L/day"));
        assertTrue(waterResp.text.contains("skin elasticity") || waterResp.text.contains("moisture barrier") || waterResp.text.contains("cellular hydration"));

        // 3. User asks about sleep when on the Nutrition recommendation
        AiChatResponse sleepResp = aiService.chatFallback(
                sampleUser, skinGoal, nutCat, skinNutRec, plan, "How does sleep help my skin?");
        assertNotNull(sleepResp);
        assertTrue(sleepResp.text.contains("Improve Skin Health"));
        assertTrue(sleepResp.text.contains("repair") || sleepResp.text.contains("collagen") || sleepResp.text.contains("cortisol"));

        // 4. User asks about exercise when on the Nutrition recommendation
        AiChatResponse exResp = aiService.chatFallback(
                sampleUser, skinGoal, nutCat, skinNutRec, plan, "What exercise should I do for my skin?");
        assertNotNull(exResp);
        assertTrue(exResp.text.contains("Improve Skin Health"));
        assertTrue(exResp.text.contains("circulation") || exResp.text.contains("oxygen"));

        // 5. User asks how this recommendation supports their goal
        AiChatResponse recResp = aiService.chatFallback(
                sampleUser, skinGoal, nutCat, skinNutRec, plan, "How does this recommendation support my Improve Skin Health goal?");
        assertNotNull(recResp);
        assertTrue(recResp.text.contains("Improve Skin Health"));
        assertTrue(recResp.text.contains("Nutrition for Healthy Skin"));
        assertTrue(recResp.text.contains("antioxidant") || recResp.text.contains("berries") || recResp.text.contains("salmon"));

        // 6. Test with chat() entrypoint with AI offline
        String prevFlag = System.getProperty("lifeforge.ai.enabled");
        try {
            System.setProperty("lifeforge.ai.enabled", "false");
            AiChatResponse offlineResp = aiService.chat(
                    sampleUser, skinGoal, nutCat, skinNutRec, plan, List.of(), "What daily habits best support my Improve Skin Health goal?");
            assertNotNull(offlineResp);
            assertTrue(offlineResp.text.contains("Improve Skin Health"));
            assertTrue(offlineResp.text.contains("Gentle cleanse") || offlineResp.text.contains("SPF"));
        } finally {
            if (prevFlag != null) {
                System.setProperty("lifeforge.ai.enabled", prevFlag);
            } else {
                System.clearProperty("lifeforge.ai.enabled");
            }
        }
    }

    @Test
    public void testSpaciousBoxedHorizontalMenuAndSelectionStates() {
        List<String> items = List.of(
                "💾 Save Recommendation",
                "💡 Why This?",
                "🤖 Chat with AI",
                "— Back"
        );

        // 1. Spacious mode (frame width 96, inner 94)
        int innerSpacious = 94;
        List<Line> sel0Spacious = ScreenKit.menuHorizontal(items, 0, innerSpacious);
        List<Line> sel1Spacious = ScreenKit.menuHorizontal(items, 1, innerSpacious);
        List<Line> sel2Spacious = ScreenKit.menuHorizontal(items, 2, innerSpacious);
        List<Line> sel3Spacious = ScreenKit.menuHorizontal(items, 3, innerSpacious);

        assertEquals(1, sel0Spacious.size());
        assertEquals(1, sel1Spacious.size());
        assertEquals(1, sel2Spacious.size());
        assertEquals(1, sel3Spacious.size());

        String s0 = sel0Spacious.get(0).text();
        String s1 = sel1Spacious.get(0).text();
        String s2 = sel2Spacious.get(0).text();
        String s3 = sel3Spacious.get(0).text();

        assertEquals("> [ 💾 Save Recommendation ]    [ 💡 Why This? ]    [ 🤖 Chat with AI ]    [ — Back ]", s0);
        assertEquals("  [ 💾 Save Recommendation ]  > [ 💡 Why This? ]    [ 🤖 Chat with AI ]    [ — Back ]", s1);
        assertEquals("  [ 💾 Save Recommendation ]    [ 💡 Why This? ]  > [ 🤖 Chat with AI ]    [ — Back ]", s2);
        assertEquals("  [ 💾 Save Recommendation ]    [ 💡 Why This? ]    [ 🤖 Chat with AI ]  > [ — Back ]", s3);

        // Ensure zero column shift across all states
        assertEquals(Theme.width(s0), Theme.width(s1));
        assertEquals(Theme.width(s0), Theme.width(s2));
        assertEquals(Theme.width(s0), Theme.width(s3));
        assertTrue(Theme.width(s0) <= innerSpacious);

        // 2. Compact mode (frame width 80, inner 78)
        int innerCompact = 78;
        List<Line> sel0Compact = ScreenKit.menuHorizontal(items, 0, innerCompact);
        List<Line> sel1Compact = ScreenKit.menuHorizontal(items, 1, innerCompact);
        List<Line> sel2Compact = ScreenKit.menuHorizontal(items, 2, innerCompact);
        List<Line> sel3Compact = ScreenKit.menuHorizontal(items, 3, innerCompact);

        String c0 = sel0Compact.get(0).text();
        String c1 = sel1Compact.get(0).text();
        String c2 = sel2Compact.get(0).text();
        String c3 = sel3Compact.get(0).text();

        assertEquals(">[ 💾 Save Recommendation ]  [ 💡 Why This? ]  [ 🤖 Chat with AI ]  [ — Back ]", c0);
        assertEquals(" [ 💾 Save Recommendation ] >[ 💡 Why This? ]  [ 🤖 Chat with AI ]  [ — Back ]", c1);
        assertEquals(" [ 💾 Save Recommendation ]  [ 💡 Why This? ] >[ 🤖 Chat with AI ]  [ — Back ]", c2);
        assertEquals(" [ 💾 Save Recommendation ]  [ 💡 Why This? ]  [ 🤖 Chat with AI ] >[ — Back ]", c3);

        assertEquals(Theme.width(c0), Theme.width(c1));
        assertEquals(Theme.width(c0), Theme.width(c2));
        assertEquals(Theme.width(c0), Theme.width(c3));
        assertEquals(78, Theme.width(c0));

        // 3. Full Page Border Alignment on spacious 96-col frame
        List<Line> body = new ArrayList<>();
        body.add(Line.of(Theme.headingCyan(), "  🍎 EAT ENOUGH PROTEIN"));
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  [ DESCRIPTION ]"));
        body.addAll(ScreenKit.paragraph("Adequate protein supports muscle growth and recovery.", 94));
        body.add(Line.blank());
        body.addAll(sel0Spacious);

        List<String[]> footer = List.of(
                new String[] { "Left/Right", "Select" },
                new String[] { "Enter", "Open / action" },
                new String[] { "B", "Back" }
        );
        String page96 = ScreenKit.page("Recommendation Detail", "Calibrated for your profile", body, "", false, footer, 96);
        assertBorderAlignment(page96, 96);
    }

    @Test
    public void testCompleteMasterRoutinePage1AndPage2Layout() {
        int frameWidth = 80;
        Recommendation mockRec = new Recommendation(
                100L, 1L, 5L, ActivityLevel.MODERATELY_ACTIVE,
                "Complete Master Routine - Build Muscle",
                "A unified lifestyle routine combining nutrition, hydration, training, and sleep.",
                "Nutrition, Exercise, Sleep, Habits, Hydration unified.",
                null, "Whole foods and progressive overload",
                "Consistency across foundational habits produces greater long-term results than short-term extremes."
        );

        List<String> menuItems = List.of(
                "💾 Save Recommendation",
                "💡 Why This?",
                "🤖 Chat with AI",
                "⬅️ Back"
        );

        List<String[]> footer = List.of(
                new String[] { "Up/Down", "Choose" },
                new String[] { "Left/Right", "Page" },
                new String[] { "Enter", "Open / action" },
                new String[] { "B", "Back" },
                new String[] { "H", "Home" },
                new String[] { "Q", "Quit" }
        );

        // ==========================================
        // PAGE 1: 5 Unified Lifestyle Pillars
        // ==========================================
        List<Line> page1Body = new ArrayList<>();
        page1Body.add(Line.of(Theme.headingCyan(), "  ⭐ COMPLETE MASTER ROUTINE"));
        page1Body.add(Line.of(Theme.dim(), "  Page 1 / 2  (Use \u2190 / \u2192 to flip pages)"));
        LifeForge.renderMasterRoutinePage1(page1Body, mockRec, frameWidth);
        page1Body.add(Line.blank());
        page1Body.addAll(ScreenKit.menuHorizontal(menuItems, 0, frameWidth - 2));

        String page1 = ScreenKit.page("Recommendation Detail", "Specific actions calibrated for your profile", page1Body, "", false, footer, frameWidth);
        assertBorderAlignment(page1, frameWidth);

        String clean1 = page1.replaceAll("\u001B\\[[;\\d]*m", "");

        // Verify Page 1 elements
        assertTrue(clean1.contains("⭐ COMPLETE MASTER ROUTINE"));
        assertTrue(clean1.contains("Page 1 / 2  (Use ← / → to flip pages)"));
        assertTrue(clean1.contains("[ DESCRIPTION ]"));
        assertTrue(clean1.contains("A unified lifestyle routine combining nutrition"));
        assertTrue(clean1.contains("sleep."));
        assertTrue(clean1.contains("📋 UNIFIED LIFESTYLE PILLARS"));
        assertTrue(clean1.contains("Nutrition"));
        assertTrue(clean1.contains("Prioritize balanced meals and"));
        assertTrue(clean1.contains("aligned with your target intake."));
        assertTrue(clean1.contains("Hydration"));
        assertTrue(clean1.contains("Maintain steady fluid intake"));
        assertTrue(clean1.contains("throughout active daytime hours."));
        assertTrue(clean1.contains("Exercise"));
        assertTrue(clean1.contains("Complete scheduled training sessions"));
        assertTrue(clean1.contains("steady progression."));
        assertTrue(clean1.contains("Recovery"));
        assertTrue(clean1.contains("Protect your nightly sleep window"));
        assertTrue(clean1.contains("support tissue adaptation."));
        assertTrue(clean1.contains("Daily Habits"));
        assertTrue(clean1.contains("Anchor small, low-friction routines"));
        assertTrue(clean1.contains("without relying"));
        assertTrue(clean1.contains("solely on motivation."));
        assertTrue(clean1.contains("[ IMPORTANT NOTES ]"));
        assertTrue(clean1.contains("Consistency across foundational habits produces"));
        assertTrue(clean1.contains("than short-term extremes."));

        // Navigation actions for Master Routine (Boxed Horizontal Menu)
        assertTrue(clean1.contains("[ 💾 Save Recommendation ]"));
        assertTrue(clean1.contains("[ 💡 Why This? ]"));
        assertTrue(clean1.contains("[ 🤖 Chat with AI ]"));
        assertTrue(clean1.contains("[ — Back ]"));
        assertTrue(clean1.contains(">[ 💾 Save Recommendation ]  [ 💡 Why This? ]  [ 🤖 Chat with AI ]  [ — Back ]"));

        // Zero metric duplication / invention
        assertFalse(clean1.contains("BMR"));
        assertFalse(clean1.contains("TDEE"));
        assertFalse(clean1.contains("Calorie Target"));
        assertFalse(clean1.contains("Hydration Target"));

        // ==========================================
        // PAGE 2: Daily Lifestyle Guidance (Rhythm)
        // ==========================================
        List<Line> page2Body = new ArrayList<>();
        page2Body.add(Line.of(Theme.headingCyan(), "  ⭐ COMPLETE MASTER ROUTINE"));
        page2Body.add(Line.of(Theme.dim(), "  Page 2 / 2  (Use \u2190 / \u2192 to flip pages)"));
        LifeForge.renderMasterRoutinePage2(page2Body, mockRec, frameWidth);
        page2Body.add(Line.blank());
        page2Body.addAll(ScreenKit.menuHorizontal(menuItems, 0, frameWidth - 2));

        String page2 = ScreenKit.page("Recommendation Detail", "Specific actions calibrated for your profile", page2Body, "", false, footer, frameWidth);
        assertBorderAlignment(page2, frameWidth);

        String clean2 = page2.replaceAll("\u001B\\[[;\\d]*m", "");

        // Verify Page 2 elements
        assertTrue(clean2.contains("⭐ COMPLETE MASTER ROUTINE"));
        assertTrue(clean2.contains("Page 2 / 2  (Use ← / → to flip pages)"));
        assertTrue(clean2.contains("🌅 DAILY LIFESTYLE GUIDANCE"));
        assertTrue(clean2.contains("Morning"));
        assertTrue(clean2.contains("Start the day with hydration and a balanced meal."));
        assertTrue(clean2.contains("Daytime"));
        assertTrue(clean2.contains("Maintain regular movement"));
        assertTrue(clean2.contains("recommended routine."));
        assertTrue(clean2.contains("Evening"));
        assertTrue(clean2.contains("Follow a balanced dinner routine"));
        assertTrue(clean2.contains("for the next day."));
        assertTrue(clean2.contains("Night"));
        assertTrue(clean2.contains("Reduce stimulating activities"));
        assertTrue(clean2.contains("consistent sleep routine."));
        assertTrue(clean2.contains("This rhythm provides a flexible structure to guide your day sustainably."));

        // Navigation actions for Master Routine (Boxed Horizontal Menu)
        assertTrue(clean2.contains("[ 💾 Save Recommendation ]"));
        assertTrue(clean2.contains("[ 💡 Why This? ]"));
        assertTrue(clean2.contains("[ 🤖 Chat with AI ]"));
        assertTrue(clean2.contains("[ — Back ]"));
        assertTrue(clean2.contains(">[ 💾 Save Recommendation ]  [ 💡 Why This? ]  [ 🤖 Chat with AI ]  [ — Back ]"));

        // Zero metric duplication / invention
        assertFalse(clean2.contains("BMR"));
        assertFalse(clean2.contains("TDEE"));
        assertFalse(clean2.contains("Calorie Target"));
        assertFalse(clean2.contains("Hydration Target"));
        assertFalse(clean2.contains("500 mL bolus"));
        assertFalse(clean2.contains("25–35 g protein"));

        // Non-tracking guardrail
        assertFalse(clean2.contains("Meal Log"));
        assertFalse(clean2.contains("Check off"));
        assertFalse(clean2.contains("Streak"));
    }

    @Test
    public void testWhyRecommendation2CardLayoutAndSecondPersonTone() {
        int frameWidth = 80;
        User user = new User(1L, "Alex Doe", "alex", "alex@example.com", "hash", 28,
                Gender.MALE, 178.0, 75.0, ActivityLevel.MODERATELY_ACTIVE, Role.USER, false, null, null);
        Goal goal = new Goal(1L, "MUSCLE_GAIN", "Build Muscle", "Build functional muscle mass", true);
        Recommendation rec = new Recommendation(
                1L, 1L, 1L, ActivityLevel.MODERATELY_ACTIVE,
                "Eat More Protein",
                "Adequate protein supports muscle growth and recovery.",
                "Include a protein-rich food source with each meal.",
                null, "120 g protein/day",
                "Maintain a balanced diet and choose a variety of nutrient-dense foods."
        );

        List<Line> body = new ArrayList<>();
        LifeForge.renderWhyRecommendation(body, rec, goal, user, null, false, frameWidth);

        List<String[]> footer = List.of(
                new String[] { "Enter/B", "Back" },
                new String[] { "H", "Home" },
                new String[] { "Q", "Quit" }
        );

        String page = ScreenKit.page("Why This Fits", "Personalized rationale", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // 1. Title & 2-Card Scaffolding
        assertTrue(clean.contains("💡 WHY THIS RECOMMENDATION FITS YOU"));
        assertTrue(clean.contains("👤 PROFILE CONTEXT"));
        assertTrue(clean.contains("💡 WHY IT FITS"));
        assertFalse(clean.contains("🔬 PHYSIOLOGICAL RATIONALE"));

        // 2. Card 1: Structured Profile Context & proper label spacing
        assertTrue(clean.contains("Active Goal       : Build Muscle"));
        assertTrue(clean.contains("Activity Level    : Moderately Active"));
        assertTrue(clean.contains("Parameters        : 75.0 kg | 178 cm | Age 28 | Male"));
        assertTrue(clean.contains("Engine Target     : 120 g protein/day"));
        assertTrue(clean.contains("Suggested Guidance: Include a protein-rich"));
        assertTrue(clean.contains("food source with each meal."));

        // 3. Card 2: Personalized WHY IT FITS bullets
        assertTrue(clean.contains("Supports Your Goal"));
        assertTrue(clean.contains("Build Muscle"));
        assertTrue(clean.contains("Fits Your Activity Level"));
        assertTrue(clean.contains("moderately active"));
        assertTrue(clean.contains("Supports Balanced Nutrition"));

        // 4. Source line
        assertTrue(clean.contains("Source: LIFEForge Rule Engine"));

        // 5. Total absence of tautological intro
        assertFalse(clean.contains("selected this recommendation because"));
        assertFalse(clean.contains("selected this because"));
        assertFalse(clean.contains("aligns with your current goal"));
        assertFalse(clean.contains("aligns with your goal"));
        assertFalse(clean.contains("Your selected goal is"));

        // 6. Strict Second-Person Tone - zero third-person medical phrasing
        assertFalse(clean.contains("this user"));
        assertFalse(clean.contains("the user"));
        assertFalse(clean.contains("the patient"));
        assertFalse(clean.matches("(?s).*\\b(his|her)\\b.*"));

        // 7. Nutrition recommendations always use personalized WHY IT FITS bullets
        //    The AI raw text is passed but the nutrition branch takes precedence (same as sleep)
        String aiRawWithThirdPerson = "• Muscle Recovery: The patient's muscle fibers require amino acids.\n"
                + "• Protein Timing: Consuming protein across her meals supports your daily metabolic rate.\n"
                + "• Glycogen Replenishment: Post-workout carbs refuel liver stores.";
        List<Line> bodyAi = new ArrayList<>();
        LifeForge.renderWhyRecommendation(bodyAi, rec, goal, user, aiRawWithThirdPerson, true, frameWidth);
        String pageAi = ScreenKit.page("Why This Fits", "Personalized rationale", bodyAi, "", false, footer, frameWidth);
        assertBorderAlignment(pageAi, frameWidth);

        String cleanAi = pageAi.replaceAll("\u001B\\[[;\\d]*m", "");
        assertTrue(cleanAi.contains("Source: LIFEForge Rule Engine + AI Explanation"));
        // Nutrition branch always uses personalized bullets — AI raw text is not parsed
        assertTrue(cleanAi.contains("Supports Your Goal"));
        assertTrue(cleanAi.contains("Build Muscle"));
        assertTrue(cleanAi.contains("Fits Your Activity Level"));
        assertTrue(cleanAi.contains("moderately active"));
        assertTrue(cleanAi.contains("Supports Balanced Nutrition"));
    }

    @Test
    public void testCard2StandardizedKeywordImpactFormatAcrossAllDomains() {
        int frameWidth = 80;
        User user = new User(1L, "Alex Doe", "alex", "alex@example.com", "hash", 28,
                Gender.MALE, 178.0, 75.0, ActivityLevel.MODERATELY_ACTIVE, Role.USER, false, null, null);
        Goal goal = new Goal(1L, "GENERAL_WELLNESS", "General Wellness", "Overall health", true);

        // Domain 1: Exercise
        Recommendation exerciseRec = new Recommendation(
                2L, 1L, 2L, ActivityLevel.MODERATELY_ACTIVE,
                "Strength & Cardio Routine",
                "Complete scheduled training sessions.",
                "Warm up and perform resistance exercises.",
                null, "30–40 minutes / day",
                "Progress gradually."
        );
        List<String> exBullets = LifeForge.buildRationaleBullets(null, exerciseRec, goal, user);
        assertEquals(3, exBullets.size());
        assertTrue(exBullets.get(0).contains("Supports Your Goal"));
        assertTrue(exBullets.get(0).contains("General Wellness"));
        assertTrue(exBullets.get(1).contains("Fits Your Activity Level"));
        assertTrue(exBullets.get(1).contains("moderately active"));
        assertTrue(exBullets.get(2).contains("Supports Progressive Training"));

        // Domain 2: Hydration
        Recommendation hydRec = new Recommendation(
                3L, 1L, 7L, ActivityLevel.MODERATELY_ACTIVE,
                "Optimal Daily Hydration",
                "Maintain steady daytime water intake.",
                "Drink water with each meal.",
                null, "2.5 L/day",
                "Carry a water bottle."
        );
        List<String> hydBullets = LifeForge.buildRationaleBullets(null, hydRec, goal, user);
        assertEquals(3, hydBullets.size());
        assertTrue(hydBullets.get(0).contains("Cellular Transport    :"));
        assertTrue(hydBullets.get(1).contains("Thermoregulation      :"));
        assertTrue(hydBullets.get(2).contains("Cognitive Stamina     :"));

        // Domain 3: Sleep & Recovery
        Recommendation sleepRec = new Recommendation(
                4L, 1L, 3L, ActivityLevel.MODERATELY_ACTIVE,
                "Restorative Sleep Protocol",
                "Protect your nightly sleep window.",
                "Turn off screens before bed.",
                null, "7–9 hours / night",
                "Keep room dark and cool."
        );
        List<String> sleepBullets = LifeForge.buildRationaleBullets(null, sleepRec, goal, user);
        assertEquals(3, sleepBullets.size());
        assertTrue(sleepBullets.get(0).contains("Supports Recovery"));
        assertTrue(sleepBullets.get(0).contains("Quality sleep supports recovery"));
        assertTrue(sleepBullets.get(1).contains("Supports Your Goal"));
        assertTrue(sleepBullets.get(1).contains("General Wellness"));
        assertTrue(sleepBullets.get(2).contains("Fits Your Activity Level"));
        assertTrue(sleepBullets.get(2).contains("moderately active"));

        // Domain 4: Habits & Master Routine
        Recommendation habitRec = new Recommendation(
                5L, 1L, 4L, ActivityLevel.MODERATELY_ACTIVE,
                "Complete Master Routine",
                "A unified lifestyle routine.",
                "Anchor foundational habits.",
                null, "Daily execution",
                "Consistency compounds."
        );
        List<String> habitBullets = LifeForge.buildRationaleBullets(null, habitRec, goal, user);
        assertEquals(3, habitBullets.size());
        assertTrue(habitBullets.get(0).contains("Behavioral Anchoring  :"));
        assertTrue(habitBullets.get(1).contains("Systemic Compounding  :"));
        assertTrue(habitBullets.get(2).contains("Lifestyle Synergy     :"));

        // Verify border alignment when rendered in full page
        List<Line> body = new ArrayList<>();
        LifeForge.renderWhyRecommendation(body, exerciseRec, goal, user, null, false, frameWidth);
        List<String[]> footer = List.of(
                new String[] { "Enter/B", "Back" },
                new String[] { "H", "Home" },
                new String[] { "Q", "Quit" }
        );
        String page = ScreenKit.page("Why This Fits", "Personalized rationale", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);
    }

    @Test
    public void testWhyRecommendationLineSpacingBetweenAllContentItems() {
        int frameWidth = 80;
        User user = new User(1L, "Alex Doe", "alex", "alex@example.com", "hash", 28,
                Gender.MALE, 178.0, 75.0, ActivityLevel.MODERATELY_ACTIVE, Role.USER, false, null, null);
        Goal goal = new Goal(1L, "BUILD_MUSCLE", "Build Muscle", "Hypertrophy", true);
        Recommendation rec = new Recommendation(
                1L, 1L, 1L, ActivityLevel.MODERATELY_ACTIVE,
                "Eat More Protein",
                "Maintain optimal daily protein intake.",
                "Include protein with every meal.",
                null, "120 g protein/day",
                "High quality sources."
        );

        List<Line> body = new ArrayList<>();
        LifeForge.renderWhyRecommendation(body, rec, goal, user, null, false, frameWidth);

        List<String[]> footer = List.of(
                new String[] { "Enter/B", "Back" },
                new String[] { "H", "Home" },
                new String[] { "Q", "Quit" }
        );
        String page = ScreenKit.page("Why This Fits", "Personalized rationale", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // Verify that empty rows (line spacing) exist between items inside the cards
        // Empty card rows look like "│  │                                                                      │  │"
        // Regex pattern: an item followed by an empty card row and then the next item
        assertTrue(clean.matches("(?s).*Active Goal.*│\\s+│.*Activity Level.*"),
                "Expected empty card spacer line between Active Goal and Activity Level");
        assertTrue(clean.matches("(?s).*Activity Level.*│\\s+│.*Parameters.*"),
                "Expected empty card spacer line between Activity Level and Parameters");
        assertTrue(clean.matches("(?s).*Supports Your Goal.*│\\s+│.*Fits Your Activity Level.*"),
                "Expected empty card spacer line between Card 2 bullets");
        assertTrue(clean.matches("(?s).*Fits Your Activity Level.*│\\s+│.*Supports Balanced Nutrition.*"),
                "Expected empty card spacer line between Card 2 bullets");
    }

    @Test
    public void testSleepRecommendationDetailPersonalization() {
        int frameWidth = 80;
        User user = new User(1L, "Sam User", "sam", "sam@example.com", "hash", 30,
                Gender.MALE, 175.0, 70.0, ActivityLevel.MODERATELY_ACTIVE, Role.USER, false, null, null);
        Goal goal = new Goal(1L, "BUILD_MUSCLE", "Build Muscle", "Muscle building", true);
        Recommendation sleepRec = new Recommendation(
                4L, 1L, 3L, ActivityLevel.MODERATELY_ACTIVE,
                "Restorative Sleep Protocol",
                "Protect your nightly sleep window.",
                "Turn off screens before bed.",
                null, "7–9 hours / night",
                "Keep room dark and cool."
        );

        List<Line> body = new ArrayList<>();
        LifeForge.renderSleepRecommendationDetail(body, sleepRec, user, goal, frameWidth);

        List<String[]> footer = List.of(
                new String[] { "Up/Down", "Move" },
                new String[] { "Enter", "Select" },
                new String[] { "Esc", "Back" }
        );
        String page = ScreenKit.page("Recommendation Detail", "Personalized Sleep & Recovery", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // 1. Verify Card 1: 🌙 SLEEP HYGIENE
        assertTrue(clean.contains("SLEEP HYGIENE"));
        assertTrue(clean.contains("Light & Screens"));
        assertTrue(clean.contains("Cut blue light and digital screens"));
        assertTrue(clean.contains("Sleep Environment"));
        assertTrue(clean.contains("Keep bedroom cool"));
        assertTrue(clean.contains("Wind-Down Routine"));
        assertTrue(clean.contains("(reading or breathwork)"));

        // 2. Verify Card 2: 💪 RECOVERY & REST
        assertTrue(clean.contains("RECOVERY & REST"));
        assertTrue(clean.contains("Sleep Schedule"));
        assertTrue(clean.contains("Target 7–9 hours / night"));
        assertTrue(clean.contains("Training Recovery"));
        assertTrue(clean.contains("Allow 48 hours of"));
        assertTrue(clean.contains("recovery between intense training sessions"));
        assertTrue(clean.contains("Evening Recovery"));
        assertTrue(clean.contains("Taper fluid intake 90 mins"));
        assertTrue(clean.contains("before bed and avoid stimulants"));

        // 3. Verify no duplicate calorie/BMR/TDEE or tracking features
        assertFalse(clean.contains("BMR"));
        assertFalse(clean.contains("TDEE"));
        assertFalse(clean.contains("log sleep"));
        assertFalse(clean.contains("sleep timer"));
        assertFalse(clean.contains("..."));
    }

    @Test
    public void testWhySleepRecommendationRationalePersonalization() {
        int frameWidth = 80;
        User user = new User(1L, "Alex Athlete", "alex", "alex@example.com", "hash", 25,
                Gender.FEMALE, 168.0, 60.0, ActivityLevel.VERY_ACTIVE, Role.USER, false, null, null);
        Goal goal = new Goal(2L, "IMPROVE_FITNESS", "Improve Fitness", "Endurance & fitness", true);
        Recommendation sleepRec = new Recommendation(
                4L, 2L, 3L, ActivityLevel.VERY_ACTIVE,
                "Restorative Sleep Protocol",
                "Protect your nightly sleep window.",
                "Turn off screens before bed.",
                null, "7–9 hours / night",
                "Keep room dark and cool."
        );

        List<Line> body = new ArrayList<>();
        LifeForge.renderWhyRecommendation(body, sleepRec, goal, user, null, false, frameWidth);

        List<String[]> footer = List.of(
                new String[] { "Enter/B", "Back" },
                new String[] { "H", "Home" },
                new String[] { "Q", "Quit" }
        );
        String page = ScreenKit.page("Why This Fits", "Personalized rationale", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // Verify Card 1: 👤 PROFILE CONTEXT
        assertTrue(clean.contains("PROFILE CONTEXT"));
        assertTrue(clean.contains("Active Goal"));
        assertTrue(clean.contains("Improve Fitness"));
        assertTrue(clean.contains("Activity Level"));
        assertTrue(clean.contains("Very Active"));

        // Verify Card 2: 💡 WHY IT FITS (renamed from PHYSIOLOGICAL RATIONALE for sleep)
        assertTrue(clean.contains("WHY IT FITS"));
        assertFalse(clean.contains("PHYSIOLOGICAL RATIONALE"));

        // Verify bullets structure and personalization
        assertTrue(clean.contains("Supports Recovery"));
        assertTrue(clean.contains("Quality sleep supports recovery"));
        assertTrue(clean.contains("Supports Your Goal"));
        assertTrue(clean.contains("Adequate rest complements your"));
        assertTrue(clean.contains("Improve Fitness"));
        assertTrue(clean.contains("Fits Your Activity Level"));
        assertTrue(clean.contains("recovery from your"));
        assertTrue(clean.contains("active activity level"));

        // Banned unsupported medical/physiological/hormone claims
        assertFalse(clean.contains("human growth hormone"));
        assertFalse(clean.contains("cortisol"));
        assertFalse(clean.contains("melatonin"));
        assertFalse(clean.contains("glycogen"));
        assertFalse(clean.contains("Endocrine Restoration"));
        assertFalse(clean.contains("Circadian Alignment"));
        assertFalse(clean.contains("Neuromuscular Reset"));
    }

    @Test
    public void testExerciseRecommendationDetailPersonalization() {
        int frameWidth = 80;

        // --- Muscle goal: Resistance Focus should appear FIRST ---
        User user = new User(1L, "Sam User", "sam", "sam@example.com", "hash", 28,
                Gender.MALE, 178.0, 75.0, ActivityLevel.MODERATELY_ACTIVE, Role.USER, false, null, null);
        Goal muscleGoal = new Goal(1L, "BUILD_MUSCLE", "Build Muscle", "Muscle building", true);
        Recommendation exerciseRec = new Recommendation(
                2L, 1L, 2L, ActivityLevel.MODERATELY_ACTIVE,
                "Strength & Cardio Routine",
                "Complete scheduled training sessions.",
                "Warm up and perform resistance exercises.",
                null, "30–40 minutes / day",
                "Progress gradually."
        );

        List<Line> body = new ArrayList<>();
        LifeForge.renderExerciseRecommendationDetail(body, exerciseRec, user, muscleGoal, frameWidth);

        List<String[]> footer = List.of(
                new String[] { "Up/Down", "Move" },
                new String[] { "Enter", "Select" },
                new String[] { "Esc", "Back" }
        );
        String page = ScreenKit.page("Recommendation Detail", "Specific actions calibrated for your profile", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // 1. Both cards are present
        assertTrue(clean.contains("RECOMMENDED EXERCISE TYPES"));
        assertTrue(clean.contains("TRAINING PARAMETERS"));

        // 2. All three exercise types present
        assertTrue(clean.contains("Resistance Focus"));
        assertTrue(clean.contains("Aerobic Base"));
        assertTrue(clean.contains("Active Mobility"));

        // 3. Resistance Focus appears before Aerobic Base for muscle goal
        int resistanceIdx = clean.indexOf("Resistance Focus");
        int aerobicIdx = clean.indexOf("Aerobic Base");
        assertTrue(resistanceIdx < aerobicIdx, "Resistance Focus should appear before Aerobic Base for Build Muscle goal");

        // 4. Training parameters present
        assertTrue(clean.contains("Target Frequency"));
        assertTrue(clean.contains("3–4 sessions / week"));
        assertTrue(clean.contains("Session Duration"));
        assertTrue(clean.contains("Intensity Zone"));

        // 5. No duplicate metrics
        assertFalse(clean.contains("BMR"));
        assertFalse(clean.contains("TDEE"));
        assertFalse(clean.contains("Calorie Target"));
    }

    @Test
    public void testWhyExerciseRecommendationRationalePersonalization() {
        int frameWidth = 80;
        User user = new User(1L, "Sam User", "sam", "sam@example.com", "hash", 28,
                Gender.MALE, 178.0, 75.0, ActivityLevel.MODERATELY_ACTIVE, Role.USER, false, null, null);
        Goal goal = new Goal(1L, "BUILD_MUSCLE", "Build Muscle", "Muscle building", true);
        Recommendation exerciseRec = new Recommendation(
                2L, 1L, 2L, ActivityLevel.MODERATELY_ACTIVE,
                "Strength & Cardio Routine",
                "Complete scheduled training sessions.",
                "Warm up and perform resistance exercises.",
                null, "30–40 minutes / day",
                "Progress gradually."
        );

        List<Line> body = new ArrayList<>();
        LifeForge.renderWhyRecommendation(body, exerciseRec, goal, user, null, false, frameWidth);

        List<String[]> footer = List.of(
                new String[] { "Enter/B", "Back" },
                new String[] { "H", "Home" },
                new String[] { "Q", "Quit" }
        );
        String page = ScreenKit.page("Why This Fits", "Personalized rationale", body, "", false, footer, frameWidth);
        assertBorderAlignment(page, frameWidth);

        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");

        // 1. Card titles
        assertTrue(clean.contains("💡 WHY THIS RECOMMENDATION FITS YOU"));
        assertTrue(clean.contains("👤 PROFILE CONTEXT"));
        assertTrue(clean.contains("💡 WHY IT FITS"));
        assertFalse(clean.contains("🔬 PHYSIOLOGICAL RATIONALE"));

        // 2. Card 1 profile context
        assertTrue(clean.contains("Active Goal       : Build Muscle"));
        assertTrue(clean.contains("Activity Level    : Moderately Active"));

        // 3. Card 2 personalized WHY IT FITS bullets
        assertTrue(clean.contains("Supports Your Goal"));
        assertTrue(clean.contains("Build Muscle"));
        assertTrue(clean.contains("Fits Your Activity Level"));
        assertTrue(clean.contains("exercise parameters"));
        assertTrue(clean.contains("moderately"));
        assertTrue(clean.contains("activity level"));
        assertTrue(clean.contains("Supports Progressive Training"));

        // 4. Source line
        assertTrue(clean.contains("Source: LIFEForge Rule Engine"));

        // 5. Banned unsupported physiological/hormone claims
        assertFalse(clean.contains("testosterone"));
        assertFalse(clean.contains("Mechanical Tension"));
        assertFalse(clean.contains("motor unit recruitment"));
        assertFalse(clean.contains("stroke volume"));
        assertFalse(clean.contains("this user"));
        assertFalse(clean.contains("the patient"));
    }

    @Test
    public void testAgePersonalizationAcrossAllCategoriesForBuildMuscle() throws SQLException {
        // 1. Setup two profiles: Age 22 vs Age 55, both Build Muscle + Moderately Active, same height & weight
        User user22 = new User();
        user22.setId(22L);
        user22.setUsername("young_athlete");
        user22.setAge(22);
        user22.setGender(Gender.MALE);
        user22.setHeightCm(178.0);
        user22.setWeightKg(75.0);
        user22.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        User user55 = new User();
        user55.setId(55L);
        user55.setUsername("mature_athlete");
        user55.setAge(55);
        user55.setGender(Gender.MALE);
        user55.setHeightCm(178.0);
        user55.setWeightKg(75.0);
        user55.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        Goal goal = new Goal(1L, "BUILD_MUSCLE", "Build Muscle", "Hypertrophy and strength development", true);

        // Setup top-level categories
        RecommendationCategory catNutrition = new RecommendationCategory(1L, "Nutrition", "Dietary guidance", null, 1);
        RecommendationCategory catExercise = new RecommendationCategory(2L, "Exercise", "Physical training", null, 2);
        RecommendationCategory catSleep = new RecommendationCategory(3L, "Sleep & Recovery", "Rest and sleep", null, 3);
        RecommendationCategory catHabits = new RecommendationCategory(4L, "Daily Micro-Habits", "Small habits", null, 4);
        RecommendationCategory catMaster = new RecommendationCategory(5L, "Complete Master Routine", "All-in-one routine", null, 5);
        RecommendationCategory catHydration = new RecommendationCategory(7L, "Hydration Guidance", "Water intake", null, 7);

        RecommendationCategoryDao fakeCatDao = new RecommendationCategoryDao() {
            @Override
            public List<RecommendationCategory> findTopLevel() {
                return List.of(catNutrition, catExercise, catSleep, catHabits, catMaster, catHydration);
            }
            @Override
            public Optional<RecommendationCategory> findById(Long id) {
                if (id == 1L) return Optional.of(catNutrition);
                if (id == 2L) return Optional.of(catExercise);
                if (id == 3L) return Optional.of(catSleep);
                if (id == 4L) return Optional.of(catHabits);
                if (id == 5L) return Optional.of(catMaster);
                if (id == 7L) return Optional.of(catHydration);
                return Optional.empty();
            }
        };

        RecommendationDao fakeRecDao = new RecommendationDao() {
            @Override
            public Optional<Recommendation> findBestMatch(Long goalId, Long categoryId, ActivityLevel activityLevel) {
                if (categoryId == 1L) {
                    return Optional.of(new Recommendation(101L, goalId, 1L, activityLevel,
                            "Protein-Focused Nutrition Plan", "Nutritional guidance",
                            "Include a strong protein source with each meal; pair with complex carbohydrates.",
                            "Approximately 500-600 kcal", "3 eggs, Greek yogurt, oats", "Spread protein evenly"));
                } else if (categoryId == 2L) {
                    return Optional.of(new Recommendation(102L, goalId, 2L, activityLevel,
                            "Strength Training Routine", "Physical training",
                            "Train each major muscle group 2x per week with progressive overload.",
                            "3-4 strength sessions per week", "Compound lifts (squats, bench, deadlift)", "Form first"));
                } else if (categoryId == 3L) {
                    return Optional.of(new Recommendation(103L, goalId, 3L, activityLevel,
                            "Sleep & Recovery Guidance", "Rest and recovery",
                            "Ensure adequate sleep and rest days between training the same muscle group.",
                            "7-9 hours per night", "Dark room, consistent bedtime", "Recovery drives adaptation"));
                } else if (categoryId == 4L) {
                    return Optional.of(new Recommendation(104L, goalId, 4L, activityLevel,
                            "Daily Micro-Habits", "Micro habits",
                            "Drink a glass of water before meals; prepare workout gear ahead of time.",
                            "3-4 micro-habits", "Morning water, meal prep", "Consistency matters"));
                } else if (categoryId == 7L) {
                    return Optional.of(new Recommendation(105L, goalId, 7L, activityLevel,
                            "Hydration Guidance", "Water intake",
                            "Sip water steadily throughout the day rather than large amounts at once.",
                            "Approximately 2.5-3.0 L/day", "Reusable water bottle", "Drink consistently"));
                }
                return Optional.empty();
            }
        };

        RecommendationPriorityResolver priorityResolver = new RecommendationPriorityResolver();
        RecommendationEngine engine = new RecommendationEngine(fakeRecDao, priorityResolver);
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        RuleBasedExplanationService explanationService = new RuleBasedExplanationService();

        RecommendationService service = new RecommendationService(
                engine,
                fakeCatDao,
                fakeRecDao,
                calorieService,
                hydrationService,
                explanationService,
                explanationService
        );

        // 2. Generate Personalized Plan for both profiles
        PersonalizedPlanResult plan22 = service.getPersonalizedPlan(user22, goal);
        PersonalizedPlanResult plan55 = service.getPersonalizedPlan(user55, goal);

        assertNotNull(plan22);
        assertNotNull(plan55);

        // 3. Verify Personalized Focus Narrative adapts by Age
        String narrative22 = plan22.getPersonalizedFocus();
        String narrative55 = plan55.getPersonalizedFocus();
        assertNotEquals(narrative22, narrative55, "Personalized focus narrative must differ between age 22 and age 55");
        assertTrue(narrative22.contains("22"), "Age 22 narrative should explicitly calibrate for age 22");
        assertTrue(narrative22.contains("25–30g") || narrative22.contains("progressive"), "Age 22 narrative reflects progressive training & standard protein feedings");
        assertTrue(narrative55.contains("55"), "Age 55 narrative should explicitly calibrate for age 55");
        assertTrue(narrative55.contains("anabolic resistance") || narrative55.contains("joint-friendly"), "Age 55 narrative reflects anabolic resistance / joint preservation");

        // 4. Verify ALL 5 categories are meaningfully personalized while preserving shared core guidance
        List<PersonalizedPlanResult.AreaItem> areas22 = plan22.getAreas();
        List<PersonalizedPlanResult.AreaItem> areas55 = plan55.getAreas();
        assertEquals(areas22.size(), areas55.size());

        for (int i = 0; i < areas22.size(); i++) {
            PersonalizedPlanResult.AreaItem item22 = areas22.get(i);
            PersonalizedPlanResult.AreaItem item55 = areas55.get(i);
            assertEquals(item22.category().getId(), item55.category().getId(), "Categories must align in order");

            Recommendation rec22 = item22.recommendation();
            Recommendation rec55 = item55.recommendation();
            assertNotNull(rec22, "Area " + item22.category().getName() + " must have a recommendation for age 22");
            assertNotNull(rec55, "Area " + item55.category().getName() + " must have a recommendation for age 55");

            long cid = item22.category().getId();
            String act22 = rec22.getRecommendedActions();
            String act55 = rec55.getRecommendedActions();

            if (cid == 1L) { // Nutrition
                // Shared guidance: both prioritize protein and carbohydrates
                assertTrue(act22.toLowerCase().contains("protein"), "Age 22 nutrition shares protein focus");
                assertTrue(act55.toLowerCase().contains("protein"), "Age 55 nutrition shares protein focus");

                // Age 22 personalization: 25-30g protein per meal, glycogen replenishment
                assertTrue(act22.contains("25–30g"), "Age 22 nutrition guides ~25-30g protein per meal for MPS");
                assertTrue(act22.contains("glycogen"), "Age 22 nutrition highlights glycogen replenishment");

                // Age 55 personalization: 35-40g+ protein to overcome anabolic resistance, joint/bone micros
                assertTrue(act55.contains("35–40g+"), "Age 55 nutrition guides 35-40g+ protein per meal");
                assertTrue(act55.contains("anabolic resistance"), "Age 55 nutrition addresses age-related anabolic resistance");
                assertTrue(act55.contains("calcium") || act55.contains("omega-3"), "Age 55 nutrition supports joint and bone matrix");
                assertNotEquals(act22, act55, "Nutrition actions must differ between age 22 and 55");

            } else if (cid == 2L) { // Exercise
                // Shared guidance: both do structured progressive resistance training
                assertTrue(act22.toLowerCase().contains("progressive overload"), "Age 22 exercise shares progressive overload");
                assertTrue(act55.toLowerCase().contains("resistance training") || act55.toLowerCase().contains("progressive overload"),
                        "Age 55 exercise shares resistance training");

                // Age 22 personalization: 6-10 rep range, standard 5-10 min warm-up
                assertTrue(act22.contains("6–10 rep"), "Age 22 exercise focuses on compound loads in 6-10 rep range");
                assertTrue(act22.contains("5–10 min"), "Age 22 exercise uses standard 5-10 min dynamic warm-up");

                // Age 55 personalization: 8-15 rep range, controlled 2-3s eccentrics, 10-15 min dynamic joint mobility prep
                assertTrue(act55.contains("8–15 rep"), "Age 55 exercise focuses on joint-friendly 8-15 rep range");
                assertTrue(act55.contains("eccentric"), "Age 55 exercise emphasizes controlled eccentric tempo");
                assertTrue(act55.contains("10–15 min"), "Age 55 exercise emphasizes dedicated 10-15 min joint mobility prep");
                assertNotEquals(act22, act55, "Exercise actions must differ between age 22 and 55");

            } else if (cid == 3L) { // Sleep & Recovery
                // Shared guidance: both aim for restorative sleep and consistent rest
                assertTrue(act22.toLowerCase().contains("sleep"), "Age 22 shares sleep guidance");
                assertTrue(act55.toLowerCase().contains("sleep"), "Age 55 shares sleep guidance");

                // Age 22 personalization: natural deep-sleep growth hormone pulses, 48-hour recovery window
                assertTrue(act22.contains("growth hormone") || act22.contains("48-hour recovery"), "Age 22 sleep emphasizes natural nocturnal GH & 48h window");

                // Age 55 personalization: 48-72 hours recovery between sessions for tendon repair, 45-60 min evening wind-down
                assertTrue(act55.contains("48–72 hours"), "Age 55 sleep emphasizes 48-72h recovery for connective tissue repair");
                assertTrue(act55.contains("wind-down"), "Age 55 sleep emphasizes calming evening wind-down");
                assertNotEquals(act22, act55, "Sleep actions must differ between age 22 and 55");

            } else if (cid == 4L) { // Daily Micro-Habits
                // Age 22 personalization: portable high-protein snacks, gym gear staging, post-meal walk
                assertTrue(act22.contains("portable high-protein snacks") || act22.contains("workout gear"), "Age 22 habits suit active young adult routine");
                assertTrue(act22.contains("screen curfew"), "Age 22 habits include digital screen curfew");

                // Age 55 personalization: 5-minute morning mobility routine, scheduled desk water, post-workout decompression
                assertTrue(act55.contains("morning mobility routine"), "Age 55 habits include 5-min morning joint mobility");
                assertTrue(act55.contains("scheduled hourly"), "Age 55 habits include scheduled desk water reminders");
                assertTrue(act55.contains("spinal decompression"), "Age 55 habits include gentle spinal decompression");
                assertNotEquals(act22, act55, "Micro-habit actions must differ between age 22 and 55");

            } else if (cid == 7L) { // Hydration Guidance
                // Shared guidance: both sip water steadily throughout active hours
                assertTrue(act22.toLowerCase().contains("water") || act22.toLowerCase().contains("hydrat"), "Age 22 shares hydration");
                assertTrue(act55.toLowerCase().contains("water") || act55.toLowerCase().contains("hydrat"), "Age 55 shares hydration");

                // Age 22 personalization: workout hydration, sweat replacement, cellular volumization
                assertTrue(act22.contains("workout sessions") || act22.contains("sweat losses"), "Age 22 hydration emphasizes training fluid replacement");

                // Age 55 personalization: clock-based schedule (diminishing thirst cues), joint lubrication, evening fluid taper
                assertTrue(act55.contains("clock-based") || act55.contains("thirst cues naturally decline"), "Age 55 hydration addresses blunted thirst cues");
                assertTrue(act55.contains("taper fluids"), "Age 55 hydration includes pre-bed fluid tapering to protect sleep");
                assertNotEquals(act22, act55, "Hydration actions must differ between age 22 and 55");

            } else if (cid == 5L) { // Complete Master Routine
                // Master routine combines all 5 pillars and must reflect age-personalized guidance
                assertTrue(act22.contains("🍎 Nutrition:"));
                assertTrue(act22.contains("🏋 Exercise:"));
                assertTrue(act22.contains("💧 Hydration:"));
                assertTrue(act22.contains("😴 Sleep & Recovery:"));
                assertTrue(act22.contains("🌱 Daily Micro-Habits:"));

                assertTrue(act55.contains("🍎 Nutrition:"));
                assertTrue(act55.contains("🏋 Exercise:"));
                assertTrue(act55.contains("💧 Hydration:"));
                assertTrue(act55.contains("😴 Sleep & Recovery:"));
                assertTrue(act55.contains("🌱 Daily Micro-Habits:"));

                assertTrue(rec22.getDescription().contains("22"), "Master routine description reflects age 22");
                assertTrue(rec55.getDescription().contains("55"), "Master routine description reflects age 55");
                assertNotEquals(act22, act55, "Master Routine combined actions must differ between age 22 and 55");
            }
        }

        // 5. Verify Daily Blueprint also adapts by Age
        DailyBlueprint bp22 = engine.buildDailyBlueprint(user22, goal);
        DailyBlueprint bp55 = engine.buildDailyBlueprint(user55, goal);

        assertNotNull(bp22);
        assertNotNull(bp55);

        // Morning
        String morning22 = bp22.getMorning().stream().map(DailyBlueprint.BlueprintItem::guidance).reduce("", (a, b) -> a + " " + b);
        String morning55 = bp55.getMorning().stream().map(DailyBlueprint.BlueprintItem::guidance).reduce("", (a, b) -> a + " " + b);
        assertTrue(morning22.contains("25–30g") || morning22.contains("glycogen"), "Age 22 morning blueprint emphasizes training glycogen and 25-30g protein");
        assertTrue(morning55.contains("35–40g+") || morning55.contains("spinal decompression"), "Age 55 morning blueprint emphasizes joint mobility and 35-40g+ protein");

        // Midday
        String midday22 = bp22.getMidday().stream().map(DailyBlueprint.BlueprintItem::guidance).reduce("", (a, b) -> a + " " + b);
        String midday55 = bp55.getMidday().stream().map(DailyBlueprint.BlueprintItem::guidance).reduce("", (a, b) -> a + " " + b);
        assertTrue(midday22.contains("progressive overload") || midday22.contains("6–10 rep"), "Age 22 midday blueprint emphasizes progressive overload");
        assertTrue(midday55.contains("joint-friendly") || midday55.contains("controlled eccentrics"), "Age 55 midday blueprint emphasizes joint-friendly controlled eccentrics");

        // Evening
        String evening22 = bp22.getEvening().stream().map(DailyBlueprint.BlueprintItem::guidance).reduce("", (a, b) -> a + " " + b);
        String evening55 = bp55.getEvening().stream().map(DailyBlueprint.BlueprintItem::guidance).reduce("", (a, b) -> a + " " + b);
        assertTrue(evening22.contains("screen curfew") || evening22.contains("growth hormone"), "Age 22 evening blueprint emphasizes screen curfew and natural GH");
        assertTrue(evening55.contains("48–72 hours") || evening55.contains("taper large liquid"), "Age 55 evening blueprint emphasizes 48-72h recovery and fluid taper");
    }
}