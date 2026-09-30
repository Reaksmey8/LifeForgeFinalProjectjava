package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.RecommendationCategory;
import com.lifeforge.service.RecommendationService;
import com.lifeforge.view.ScreenKit.Line;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class RecommendationDetailCategoryMappingTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    private LifeForge createTuiWithState(Goal goal, RecommendationCategory category, Recommendation recommendation) throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());

        Field widthField = LifeForge.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.set(tui, 80);

        Field categoryField = LifeForge.class.getDeclaredField("currentCategory");
        categoryField.setAccessible(true);
        categoryField.set(tui, category);

        RecommendationService.RecommendationResult recResult = new RecommendationService.RecommendationResult(
                recommendation, null, 2.5, false
        );

        Field resultField = LifeForge.class.getDeclaredField("result");
        resultField.setAccessible(true);
        resultField.set(tui, recResult);

        return tui;
    }

    private String renderDetailBody(LifeForge tui) throws Exception {
        List<Line> body = new ArrayList<>();
        Method viewDetailMethod = LifeForge.class.getDeclaredMethod("viewRecommendDetail", List.class);
        viewDetailMethod.setAccessible(true);
        viewDetailMethod.invoke(tui, body);

        return body.stream().map(Line::text).collect(Collectors.joining("\n"));
    }

    @Test
    public void testGainWeightHydrationDetailRendersHydrationNotExercise() throws Exception {
        Goal gainWeightGoal = new Goal(2L, "GAIN_WEIGHT", "Gain Weight", "Build lean muscle and healthy mass", true);
        RecommendationCategory hydrationCategory = new RecommendationCategory(7L, "Hydration Guidance", "Water intake protocols", null, 1);

        // Recommendation 15 from lifeforge_db: Goal 2, Cat 7
        // Notice description mentions "exercise performance" and "recovery"
        Recommendation rec15 = new Recommendation();
        rec15.setId(15L);
        rec15.setGoalId(2L);
        rec15.setCategoryId(7L);
        rec15.setTitle("Stay Properly Hydrated");
        rec15.setDescription("Adequate hydration supports digestion, exercise performance, and recovery during a weight-gain plan.");
        rec15.setSuggestedTarget("3.0 L/day");
        rec15.setRecommendedActions("Drink water consistently throughout the day and with meals.");
        rec15.setImportantNotes("Adjust intake based on physical activity and ambient temperature.");

        LifeForge tui = createTuiWithState(gainWeightGoal, hydrationCategory, rec15);
        String rendered = renderDetailBody(tui);

        // MUST render Hydration guidance cards
        assertTrue(rendered.contains("HYDRATION TIMING PROTOCOL"),
                "Expected Hydration Timing Protocol card but got:\n" + rendered);
        assertTrue(rendered.contains("INTAKE & ELECTROLYTE PARAMETERS"),
                "Expected Intake & Electrolyte Parameters card but got:\n" + rendered);

        // MUST NOT render Exercise guidance cards despite 'exercise' in description
        assertFalse(rendered.contains("RECOMMENDED EXERCISE TYPES"),
                "Hydration detail should NOT render exercise types");
        assertFalse(rendered.contains("TRAINING PARAMETERS"),
                "Hydration detail should NOT render training parameters");
        assertFalse(rendered.contains("NUTRITION GUIDANCE"),
                "Hydration detail should NOT render nutrition guidance");
        assertFalse(rendered.contains("SLEEP HYGIENE"),
                "Hydration detail should NOT render sleep hygiene");
    }

    @Test
    public void testExerciseDetailRendersExerciseNotOtherCategories() throws Exception {
        Goal gainWeightGoal = new Goal(2L, "GAIN_WEIGHT", "Gain Weight", "Build lean muscle and healthy mass", true);
        RecommendationCategory exerciseCategory = new RecommendationCategory(2L, "Exercise Guidance", "Physical training routines", null, 2);

        Recommendation rec = new Recommendation();
        rec.setId(20L);
        rec.setGoalId(2L);
        rec.setCategoryId(2L);
        rec.setTitle("Hypertrophy Resistance Training");
        rec.setDescription("Follow progressive overload resistance training with sufficient rest and hydration.");
        rec.setSuggestedTarget("3–4 sessions / week");
        rec.setRecommendedActions("Focus on compound lifts and steady progression.");

        LifeForge tui = createTuiWithState(gainWeightGoal, exerciseCategory, rec);
        String rendered = renderDetailBody(tui);

        // MUST render Exercise guidance cards
        assertTrue(rendered.contains("RECOMMENDED EXERCISE TYPES"),
                "Expected Recommended Exercise Types card but got:\n" + rendered);
        assertTrue(rendered.contains("TRAINING PARAMETERS"),
                "Expected Training Parameters card but got:\n" + rendered);

        // MUST NOT render other categories
        assertFalse(rendered.contains("HYDRATION TIMING PROTOCOL"),
                "Exercise detail should NOT render hydration protocol");
        assertFalse(rendered.contains("INTAKE & ELECTROLYTE PARAMETERS"),
                "Exercise detail should NOT render hydration parameters");
        assertFalse(rendered.contains("SLEEP HYGIENE"),
                "Exercise detail should NOT render sleep hygiene");
    }

    @Test
    public void testNutritionDetailRendersNutritionNotOtherCategories() throws Exception {
        Goal gainWeightGoal = new Goal(2L, "GAIN_WEIGHT", "Gain Weight", "Build lean muscle and healthy mass", true);
        RecommendationCategory nutritionCategory = new RecommendationCategory(1L, "Nutrition", "Dietary and caloric guidelines", null, 3);

        Recommendation rec = new Recommendation();
        rec.setId(10L);
        rec.setGoalId(2L);
        rec.setCategoryId(1L);
        rec.setTitle("Caloric Surplus & Protein Density");
        rec.setDescription("Consume a caloric surplus with adequate protein to fuel training and recovery.");
        rec.setSuggestedTarget("140 g protein/day");

        LifeForge tui = createTuiWithState(gainWeightGoal, nutritionCategory, rec);
        String rendered = renderDetailBody(tui);

        // MUST render Nutrition guidance cards
        assertTrue(rendered.contains("NUTRITION GUIDANCE"),
                "Expected Nutrition Guidance card but got:\n" + rendered);
        assertTrue(rendered.contains("FOOD SOURCES"),
                "Expected Food Sources card but got:\n" + rendered);

        // MUST NOT render Exercise or Hydration
        assertFalse(rendered.contains("RECOMMENDED EXERCISE TYPES"),
                "Nutrition detail should NOT render exercise types");
        assertFalse(rendered.contains("HYDRATION TIMING PROTOCOL"),
                "Nutrition detail should NOT render hydration protocol");
    }

    @Test
    public void testSleepRecoveryDetailRendersSleepNotOtherCategories() throws Exception {
        Goal gainWeightGoal = new Goal(2L, "GAIN_WEIGHT", "Gain Weight", "Build lean muscle and healthy mass", true);
        RecommendationCategory sleepCategory = new RecommendationCategory(3L, "Sleep & Recovery", "Rest and sleep optimization", null, 4);

        Recommendation rec = new Recommendation();
        rec.setId(30L);
        rec.setGoalId(2L);
        rec.setCategoryId(3L);
        rec.setTitle("Deep Sleep & Recovery Architecture");
        rec.setDescription("Optimize rest intervals and sleep hygiene to maximize recovery from workouts.");
        rec.setSuggestedTarget("7–9 hours / night");

        LifeForge tui = createTuiWithState(gainWeightGoal, sleepCategory, rec);
        String rendered = renderDetailBody(tui);

        // MUST render Sleep guidance cards
        assertTrue(rendered.contains("SLEEP HYGIENE"),
                "Expected Sleep Hygiene card but got:\n" + rendered);
        assertTrue(rendered.contains("RECOVERY & REST"),
                "Expected Recovery & Rest card but got:\n" + rendered);

        // MUST NOT render Exercise or Hydration
        assertFalse(rendered.contains("RECOMMENDED EXERCISE TYPES"),
                "Sleep detail should NOT render exercise types");
        assertFalse(rendered.contains("HYDRATION TIMING PROTOCOL"),
                "Sleep detail should NOT render hydration protocol");
        assertFalse(rendered.contains("NUTRITION GUIDANCE"),
                "Sleep detail should NOT render nutrition guidance");
    }

    @Test
    public void testCustomCategoryIdsBySubcategoryOrNameMatching() throws Exception {
        Goal goal = new Goal(2L, "GAIN_WEIGHT", "Gain Weight", "Build lean muscle", true);

        // Test custom mock category ID (e.g. 99L) with name "Hydration Guidance"
        RecommendationCategory customHydrationCat = new RecommendationCategory(99L, "Hydration Guidance", "Water rules", null, 5);
        Recommendation recHydration = new Recommendation();
        recHydration.setTitle("Daily Hydration Routine");
        recHydration.setDescription("Supports digestion and exercise performance.");
        recHydration.setCategoryId(99L);

        LifeForge tui = createTuiWithState(goal, customHydrationCat, recHydration);
        String rendered = renderDetailBody(tui);

        assertTrue(rendered.contains("HYDRATION TIMING PROTOCOL"));
        assertFalse(rendered.contains("RECOMMENDED EXERCISE TYPES"));
    }
}
