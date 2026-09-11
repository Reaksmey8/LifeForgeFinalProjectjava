package com.lifeforge.engine;

import com.lifeforge.dao.RecommendationCategoryDao;
import com.lifeforge.dao.RecommendationDao;
import com.lifeforge.model.*;
import com.lifeforge.service.*;
import com.lifeforge.service.CalorieService;
import com.lifeforge.service.RecommendationPriorityResolver;
import com.lifeforge.tui4j.ScreenKit;
import com.lifeforge.tui4j.ScreenKit.Line;
import com.lifeforge.tui4j.Theme;
import com.lifeforge.util.CalorieCalculator;
import com.lifeforge.util.HydrationCalculator;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
        assertEquals(GoalCompatibilityStatus.Level.WARNING, status90.getLevel());
        assertTrue(status90.isWarning());
        assertEquals("⚠ WARNING", status90.getLevel().getTitle());
        assertTrue(status90.getProfileSummary().contains("18 yrs"));
        assertTrue(status90.getProfileSummary().contains("160 cm"));
        assertTrue(status90.getProfileSummary().contains("90 kg"));
        assertTrue(status90.getProfileSummary().contains("BMI 35.2"));

        assertFalse(status90.getRecommendations().isEmpty());
        assertTrue(status90.getRecommendations().stream().anyMatch(r -> r.contains("healthy weight management")));
        assertTrue(status90.getRecommendations().stream().anyMatch(r -> r.contains("fitness")));

        // Verify card rendering
        List<Line> card = status90.renderCard(74);
        assertNotNull(card);
        assertTrue(card.size() >= 8);
        assertTrue(card.get(0).text().contains("┌"));
        assertTrue(card.get(card.size() - 1).text().contains("└"));

        // 20 yrs, 160 cm, 50 kg, Gain Weight -> WELL_ALIGNED
        User u50kg = new User();
        u50kg.setAge(20);
        u50kg.setGender(Gender.FEMALE);
        u50kg.setHeightCm(160.0);
        u50kg.setWeightKg(50.0);
        u50kg.setActivityLevel(ActivityLevel.SEDENTARY);

        GoalCompatibilityStatus status50 = GoalCompatibilityStatus.compute(u50kg, gainGoal);
        assertEquals(GoalCompatibilityStatus.Level.WELL_ALIGNED, status50.getLevel());
        assertFalse(status50.isWarning());
        assertEquals("✓ WELL ALIGNED", status50.getLevel().getTitle());
        assertTrue(status50.getRecommendations().stream().anyMatch(r -> r.contains("gradual weight gain")));

        // 18 yrs, 160 cm, 90 kg, Lose Weight -> WELL_ALIGNED
        GoalCompatibilityStatus statusLose90 = GoalCompatibilityStatus.compute(u90kg, loseGoal);
        assertEquals(GoalCompatibilityStatus.Level.WELL_ALIGNED, statusLose90.getLevel());
        assertFalse(statusLose90.isWarning());
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
        String[] emojis = { "🔴", "🟡", "🔵", "⭐", "✅", "❌", "✏", "➕", "💧", "🍎", "🏃", "🍽", "🏋", "🧘", "😴", "🎯", "⚠", "⚙" };
        for (String emoji : emojis) {
            int displayW = Theme.width(emoji);
            assertEquals(2, displayW, "Emoji " + emoji + " should have display width 2");
        }
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
        blueprintBody.add(Line.of(Theme.text(), "    🏋 Exercise: Progressive overload resistance training"));
        blueprintBody.add(Line.of(Theme.text(), "    🍽 Lunch: Balanced meal with whole foods"));
        blueprintBody.add(Line.blank());
        blueprintBody.add(Line.of(Theme.headingPurple(), "  EVENING RHYTHM"));
        blueprintBody.add(Line.of(Theme.text(), "    🧘 Recovery: Dim lights and limit screens"));
        blueprintBody.add(Line.of(Theme.text(), "    😴 Sleep: 7-9 hours restorative rest"));
        blueprintBody.add(Line.blank());

        String blueprintPage = ScreenKit.page("Daily Blueprint", "Recommended daily rhythm", blueprintBody, "", false, footer, frameWidth);
        assertBorderAlignment(blueprintPage, frameWidth);
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
        assertEquals(-1L, rec.getId());
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
        assertEquals(-1L, generated.get().recommendation.getId());
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
}