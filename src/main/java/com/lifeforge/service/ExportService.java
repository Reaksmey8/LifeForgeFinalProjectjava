package com.lifeforge.service;

import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assembles the comprehensive diagnostic data map that JasperReportsService feeds into the
 * lifeForge_report.jrxml template.
 *
 * <p>Includes clinical BMI compatibility checks (intercepting contraindicated caloric deficits
 * for underweight subjects), metabolic Mifflin-St Jeor calculations, dynamic lifestyle guidance,
 * dynamic Ollama AI synthesis (with clinical fallback), and structured action checklist targets.</p>
 */
public class ExportService {

    private final CalorieService calorieService;
    private final HydrationService hydrationService;
    private final RecommendationExplanationService aiExplanationService;

    private static final java.awt.Image iconNutrition = loadIcon("/images/icon_nutrition.png");
    private static final java.awt.Image iconExercise = loadIcon("/images/icon_exercise.png");
    private static final java.awt.Image iconHydration = loadIcon("/images/icon_hydration.png");
    private static final java.awt.Image iconSleep = loadIcon("/images/icon_sleep.png");
    private static final java.awt.Image iconHabits = loadIcon("/images/icon_habits.png");
    private static final java.awt.Image iconSparkle = loadIcon("/images/icon_sparkle.png");
    private static final java.awt.Image iconWarning = loadIcon("/images/icon_warning.png");
    private static final java.awt.Image iconProtein = loadIcon("/images/icon_protein.png");
    private static final java.awt.Image iconCarbs = loadIcon("/images/icon_carbs.png");
    private static final java.awt.Image iconFats = loadIcon("/images/icon_fats.png");

    private static java.awt.Image loadIcon(String resourcePath) {
        try (var is = ExportService.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                return javax.imageio.ImageIO.read(is);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public ExportService(CalorieService calorieService, HydrationService hydrationService) {
        this(calorieService, hydrationService, null);
    }

    public ExportService(CalorieService calorieService,
                         HydrationService hydrationService,
                         RecommendationExplanationService aiExplanationService) {
        this.calorieService = calorieService;
        this.hydrationService = hydrationService;
        this.aiExplanationService = aiExplanationService;
    }

    public Map<String, Object> buildReportData(User user, Goal goal, List<Recommendation> recommendations) {
        Map<String, Object> data = new HashMap<>();

        // Embed pre-loaded icon assets
        data.put("iconNutrition", iconNutrition);
        data.put("iconExercise", iconExercise);
        data.put("iconHydration", iconHydration);
        data.put("iconSleep", iconSleep);
        data.put("iconHabits", iconHabits);
        data.put("iconSparkle", iconSparkle);
        data.put("iconWarning", iconWarning);
        data.put("iconProtein", iconProtein);
        data.put("iconCarbs", iconCarbs);
        data.put("iconFats", iconFats);

        // 1. User Demographic & Metric Baseline (Safe Math Guards)
        String fullName = (user != null && user.getFullName() != null && !user.getFullName().isBlank()) ? user.getFullName() : "LifeForge User";
        String email = (user != null && user.getEmail() != null && !user.getEmail().isBlank()) ? user.getEmail() : "N/A";
        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;
        String gender = (user != null && user.getGender() != null) ? user.getGender().name() : "MALE";
        double heightCm = (user != null && user.getHeightCm() != null && user.getHeightCm() > 0) ? user.getHeightCm() : 175.0;
        double weightKg = (user != null && user.getWeightKg() != null && user.getWeightKg() > 0) ? user.getWeightKg() : 70.0;
        if (weightKg <= 0) weightKg = 70.0;
        if (heightCm <= 0) heightCm = 175.0;

        String activityLevel = (user != null && user.getActivityLevel() != null)
                ? user.getActivityLevel().getDescription()
                : "Moderately Active (3-5 days/week)";

        double bmi = (user != null && user.getBmi() != null && user.getBmi() > 0)
                ? user.getBmi()
                : User.calculateBmi(weightKg, heightCm);
        if (Double.isNaN(bmi) || Double.isInfinite(bmi) || bmi <= 0) {
            bmi = 22.0;
        }
        bmi = Math.round(bmi * 10.0) / 10.0;

        String goalName = (goal != null && goal.getName() != null && !goal.getName().isBlank()) ? goal.getName() : "General Health & Vitality";
        String goalCode = (goal != null && goal.getCode() != null && !goal.getCode().isBlank()) ? goal.getCode() : "GENERAL";

        // Dual identity parameter binding
        data.put("fullName", fullName);
        data.put("USER_NAME", fullName);
        data.put("email", email);
        data.put("age", age);
        data.put("USER_AGE", age);
        data.put("gender", gender);
        data.put("USER_GENDER", gender);
        data.put("heightCm", heightCm);
        data.put("USER_HEIGHT", heightCm);
        data.put("weightKg", weightKg);
        data.put("USER_WEIGHT", weightKg);
        data.put("bmi", bmi);
        data.put("USER_BMI", bmi);
        data.put("activityLevel", activityLevel);
        data.put("goalName", goalName);
        data.put("USER_GOAL", goalName);
        data.put("goalCode", goalCode);

        // 2. Metabolic Benchmark Calculations
        CalorieService.CalorieSummary calorieSummary = null;
        if (calorieService != null && user != null && goal != null) {
            try {
                if (user.getGender() != null && user.getActivityLevel() != null && user.getAge() != null
                        && user.getWeightKg() != null && user.getHeightCm() != null) {
                    calorieSummary = calorieService.calculateFor(user, goal);
                }
            } catch (Exception ignored) {}
        }
        double baseBmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) + ("FEMALE".equalsIgnoreCase(gender) ? -161 : 5);
        double defaultBmr = Math.round(baseBmr / 10.0) * 10.0;
        double defaultTdee = Math.round((defaultBmr * 1.55) / 10.0) * 10.0;
        double bmr = (calorieSummary != null && calorieSummary.bmr > 0) ? calorieSummary.bmr : defaultBmr;
        double tdee = (calorieSummary != null && calorieSummary.tdee > 0) ? calorieSummary.tdee : defaultTdee;
        double suggestedCalorieTarget = (calorieSummary != null && calorieSummary.suggestedTarget > 0) ? calorieSummary.suggestedTarget : tdee;

        double suggestedHydrationLiters = 2.5;
        if (hydrationService != null && user != null) {
            try {
                if (user.getWeightKg() != null && user.getActivityLevel() != null) {
                    suggestedHydrationLiters = hydrationService.suggestedLitersPerDay(user);
                } else {
                    suggestedHydrationLiters = (weightKg * 0.033) + 0.4;
                }
            } catch (Exception ignored) {
                suggestedHydrationLiters = (weightKg * 0.033) + 0.4;
            }
        }
        if (suggestedHydrationLiters <= 0) suggestedHydrationLiters = 2.5;

        // 3. Clinical Safety Interception (Underweight + Deficit Conflict)
        boolean isUnderweight = (bmi > 0 && bmi < 18.5);
        String codeUpper = goalCode.toUpperCase(Locale.ROOT);
        String nameUpper = goalName.toUpperCase(Locale.ROOT);
        boolean isDeficitGoal = codeUpper.contains("LOSE") || codeUpper.contains("DEFICIT")
                || nameUpper.contains("LOSE") || nameUpper.contains("WEIGHT LOSS");
        boolean isUnderweightDeficitWarning = isUnderweight && isDeficitGoal;

        if (isUnderweightDeficitWarning) {
            // Override deficit: Calibrate toward maintenance (TDEE) to preserve physiological stability
            suggestedCalorieTarget = tdee;
            data.put("isUnderweightDeficitWarning", Boolean.TRUE);
            data.put("goalStatusLevel", "WARNING");
            data.put("goalStatusTitle", "CLINICAL MISALIGNMENT DETECTED");
            data.put("goalStatusDescription", "Current BMI indicates you are underweight (< 18.5). Active deficit is suspended; daily intake calibrated to maintenance to protect lean functional mass.");
            data.put("goalStatusWarningMessage",
                    "⚠️ CAUTION: CLINICAL MISALIGNMENT — Current BMI indicates you are underweight (<18.5). Active deficit is suspended; daily intake calibrated to maintenance to protect lean functional mass.");
        } else {
            data.put("isUnderweightDeficitWarning", Boolean.FALSE);
            data.put("goalStatusLevel", "WELL_ALIGNED");
            data.put("goalStatusTitle", "WELL ALIGNED WITH CLINICAL OBJECTIVES");
            data.put("goalStatusDescription", "Your biological profile and activity level are well aligned with your active health goal: "
                    + goalName + ". All targets reflect evidence-based guidelines.");
            data.put("goalStatusWarningMessage", "");
        }

        double targetCals = suggestedCalorieTarget;
        if (targetCals <= 0) {
            targetCals = (tdee > 0) ? tdee : 2000.0;
        }

        // Dual benchmark parameter binding
        data.put("bmr", bmr);
        data.put("BMR_VALUE", bmr);
        data.put("tdee", tdee);
        data.put("TDEE_VALUE", tdee);
        data.put("suggestedCalorieTarget", suggestedCalorieTarget);
        data.put("CALORIE_TARGET", suggestedCalorieTarget);
        data.put("TARGET_CALORIES", String.format(Locale.ROOT, "%,.0f", targetCals));
        data.put("suggestedHydrationLiters", suggestedHydrationLiters);
        data.put("HYDRATION_TARGET", suggestedHydrationLiters);

        // 4. Dynamic Protocol Guidelines
        data.put("hydrationGuidance", String.format(Locale.ROOT,
                "Consume %.1f L of clean water daily (33 mL/kg body mass + activity bonus). Distribute intake evenly across daylight hours to optimize cellular hydration and metabolic waste clearance.",
                suggestedHydrationLiters));
        data.put("nutritionGuidance", String.format(Locale.ROOT,
                "Target %.0f kcal/day emphasizing whole-food macronutrient distribution: 25-30%% quality protein, 45-50%% complex low-glycemic carbohydrates, and 25-30%% essential fatty acids.",
                targetCals));
        data.put("exerciseGuidance",
                "Execute 3-5 sessions weekly combining compound resistance movements with aerobic conditioning. Prioritize progressive overload, strict form, and structured recovery periods.");
        data.put("sleepGuidance",
                "Maintain a 7.0 - 9.0 hour sleep window with circadian synchronization. Keep sleeping environment dark, silent, and temperature-regulated between 18-20°C.");
        data.put("habitsGuidance",
                "Build frictionless daily micro-habits: 500 mL water upon waking, 10 min post-meal mobility walk, and dim screens 60 minutes before bed.");

        // 5. Dynamic Macronutrient & Fueling Blueprint (Page 1 Section 4)
        boolean isGain = codeUpper.contains("GAIN") || nameUpper.contains("GAIN")
                || codeUpper.contains("MUSCLE") || nameUpper.contains("MUSCLE");

        double pPerKgLow, pPerKgHigh;
        String proteinRatioStr;
        String proteinFocus, carbFocus, fatFocus;
        String proteinSources, carbSources, fatSources;

        if (isDeficitGoal) {
            pPerKgLow = 1.8;
            pPerKgHigh = 2.2;
            proteinRatioStr = "1.8 - 2.2";
            proteinFocus = "Muscle retention, elevated satiety, and positive nitrogen balance during caloric deficit.";
            carbFocus = "Glycogen restoration and sustained workout capacity while maintaining targeted fat oxidation.";
            fatFocus = "Endocrine regulation, essential fatty acids, and cellular membrane integrity.";
            proteinSources = "Chicken breast, egg whites, Greek yogurt, lean beef, tofu.";
            carbSources = "Rolled oats, quinoa, brown rice, sweet potatoes, whole grains.";
            fatSources = "Avocado, extra virgin olive oil, walnuts, chia seeds.";
        } else if (isGain) {
            pPerKgLow = 2.0;
            pPerKgHigh = 2.26; // Mathematical proportionality: 72kg -> 145-165g, 65kg -> 130-145g
            proteinRatioStr = "2.0 - 2.2";
            proteinFocus = "Muscle protein synthesis, tissue repair, and nitrogen retention.";
            carbFocus = "Glycogen restoration and sustained resistance training endurance.";
            fatFocus = "Hormone balance, joint protection, and lipid-soluble vitamin absorption.";
            proteinSources = "Poultry, wild fish, eggs, Greek yogurt, tofu.";
            carbSources = "Brown rice, rolled oats, sweet potatoes, whole grains.";
            fatSources = "Avocado, raw almonds, extra virgin olive oil, pumpkin seeds.";
        } else {
            pPerKgLow = 1.4;
            pPerKgHigh = 1.8;
            proteinRatioStr = "1.4 - 1.8";
            proteinFocus = "Cellular repair, metabolic enzymatic regulation, and lean tissue preservation.";
            carbFocus = "Consistent daylight stamina, cognitive performance, and blood glucose stability.";
            fatFocus = "Cardiovascular wellness, lipid-soluble vitamin assimilation, and cellular health.";
            proteinSources = "Pasture-raised eggs, salmon, lentils, tempeh, poultry.";
            carbSources = "Whole grains, berries, roasted root vegetables, legumes.";
            fatSources = "Extra virgin olive oil, flaxseeds, almonds, mixed olives.";
        }

        int protLowGrams = (int) (Math.round((weightKg * pPerKgLow) / 5.0) * 5);
        int protHighGrams = (int) (Math.round((weightKg * pPerKgHigh) / 5.0) * 5);
        if (protHighGrams <= protLowGrams) {
            protHighGrams = protLowGrams + 10;
        }

        int protMidGrams = (protLowGrams + protHighGrams) / 2;
        int protKcal = protMidGrams * 4;
        int protPct = (int) Math.round(((double) protKcal / targetCals) * 100);

        // Fats: 20% to 23.5% of caloric budget (9 kcal/g) -> 65-75g for 2900 kcal, 55-65g for 2480 kcal
        int fatLowKcal = (int) Math.round(targetCals * 0.20);
        int fatHighKcal = (int) Math.round(targetCals * 0.235);
        int fatLowGrams = (int) (Math.round((fatLowKcal / 9.0) / 5.0) * 5);
        int fatHighGrams = (int) (Math.round((fatHighKcal / 9.0) / 5.0) * 5);
        if (fatHighGrams <= fatLowGrams) {
            fatHighGrams = fatLowGrams + 10;
        }
        int fatMidGrams = (fatLowGrams + fatHighGrams) / 2;
        int fatKcal = fatMidGrams * 9;
        int fatPct = (int) Math.round(((double) fatKcal / targetCals) * 100);

        // Carbs: 48% to 52% of caloric budget (4 kcal/g) -> 340-365g for 2900 kcal, 310-330g for 2480 kcal
        int carbLowGrams, carbHighGrams;
        if (isGain) {
            double carbRatio = 0.52 - Math.max(0.0, Math.min(1.0, (targetCals - 2400.0) / 500.0)) * 0.035;
            int baseMid = (int) (Math.round(((targetCals * carbRatio) / 4.0) / 5.0) * 5);
            if (targetCals >= 2800) {
                carbLowGrams = baseMid - 10; // 350 - 10 = 340
                carbHighGrams = baseMid + 15; // 350 + 15 = 365
            } else {
                carbLowGrams = baseMid - 10; // 320 - 10 = 310
                carbHighGrams = baseMid + 10; // 320 + 10 = 330
            }
        } else if (isDeficitGoal) {
            int baseMid = (int) (Math.round(((targetCals * 0.42) / 4.0) / 5.0) * 5);
            carbLowGrams = Math.max(50, baseMid - 15);
            carbHighGrams = baseMid + 15;
        } else {
            int baseMid = (int) (Math.round(((targetCals * 0.48) / 4.0) / 5.0) * 5);
            carbLowGrams = Math.max(50, baseMid - 15);
            carbHighGrams = baseMid + 15;
        }
        int carbMidGrams = (carbLowGrams + carbHighGrams) / 2;
        int carbKcal = carbMidGrams * 4;
        int carbPct = (int) Math.round(((double) carbKcal / targetCals) * 100);

        String proteinRange = protLowGrams + " - " + protHighGrams;
        String carbRange = carbLowGrams + " - " + carbHighGrams;
        String fatRange = fatLowGrams + " - " + fatHighGrams;

        data.put("PROTEIN_RANGE", proteinRange);
        data.put("PROTEIN_GRAMS", proteinRange);
        data.put("PROTEIN_PER_KG", proteinRatioStr);
        data.put("PROTEIN_RATIO", proteinRatioStr);
        data.put("PROTEIN_CALORIE_DESC", String.format(Locale.ROOT, "~%,d kcal (~%d%% of daily energy)", protKcal, protPct));
        data.put("PROTEIN_FOCUS", proteinFocus);
        data.put("PROTEIN_SOURCES", proteinSources);

        data.put("CARB_RANGE", carbRange);
        data.put("CARB_GRAMS", carbRange);
        data.put("CARB_CALORIE_DESC", String.format(Locale.ROOT, "~%,d kcal (~%d%% of daily energy)", carbKcal, carbPct));
        data.put("CARB_FOCUS", carbFocus);
        data.put("CARB_SOURCES", carbSources);

        data.put("FAT_RANGE", fatRange);
        data.put("FAT_GRAMS", fatRange);
        data.put("FAT_CALORIE_DESC", String.format(Locale.ROOT, "~%,d kcal (~%d%% of daily energy)", fatKcal, fatPct));
        data.put("FAT_FOCUS", fatFocus);
        data.put("FAT_SOURCES", fatSources);

        // Meal Cadence Strip
        int sittingLow = Math.max(20, (protLowGrams / 4) / 5 * 5);
        int sittingHigh = Math.max(sittingLow + 5, (protHighGrams / 4) / 5 * 5 + 5);
        String cadenceDesc;
        if (isGain) {
            cadenceDesc = "Fueling Cadence: Distribute macros across 3-4 balanced meals + 1 post-workout recovery sitting ("
                    + sittingLow + "-" + sittingHigh + "g protein per sitting spaced 3.5-4 hours apart).";
        } else if (isDeficitGoal) {
            cadenceDesc = "Fueling Cadence: Distribute macros across 3 structured meals + 1 high-satiety protein snack ("
                    + sittingLow + "-" + sittingHigh + "g protein per sitting to suppress hunger).";
        } else {
            cadenceDesc = "Fueling Cadence: Distribute macros across 3 regular meals evenly spaced throughout daylight hours ("
                    + sittingLow + "-" + sittingHigh + "g protein per sitting).";
        }
        data.put("MACRO_CADENCE", cadenceDesc);

        // 6. Dynamic AI Clinical Rationale (Page 1)
        String aiExplanation = null;
        if (aiExplanationService != null && aiExplanationService.isAvailable() && recommendations != null && !recommendations.isEmpty()) {
            try {
                ExplanationOutcome outcome = aiExplanationService.explain(
                        user, goal, (user != null ? user.getActivityLevel() : null), recommendations.get(0));
                if (outcome != null && outcome.text != null && !outcome.text.isBlank() && outcome.fromAi) {
                    aiExplanation = outcome.text;
                }
            } catch (Exception ignored) {
            }
        }
        if (aiExplanation == null || aiExplanation.isBlank()) {
            aiExplanation = "CLINICAL ASSESSMENT & SYNTHESIS:\n\n"
                    + "Based on comprehensive metabolic profiling utilizing the Mifflin-St Jeor equation, "
                    + fullName
                    + " presents with a Basal Metabolic Rate (BMR) of " + String.format(Locale.ROOT, "%.0f", bmr)
                    + " kcal/day and a Total Daily Energy Expenditure (TDEE) of "
                    + String.format(Locale.ROOT, "%.0f", tdee) + " kcal/day, adjusted for a "
                    + activityLevel
                    + " lifestyle.\n\n"
                    + (isUnderweightDeficitWarning
                        ? "CRITICAL SAFETY INTERCEPTION: The subject's BMI of " + String.format(Locale.ROOT, "%.1f", bmi)
                            + " falls below the clinically normal threshold (< 18.5). Although the registered goal is weight loss, prescribing a caloric deficit to an underweight individual contradicts clinical nutritional safety. The LIFEForge engine has safely intercepted this deficit and calibrated intake toward metabolic maintenance ("
                            + String.format(Locale.ROOT, "%.0f", targetCals)
                            + " kcal/day) to promote muscle retention, energy stabilization, and hormonal balance.\n\n"
                            : "GOAL ALIGNMENT: The recommended caloric target of " + String.format(Locale.ROOT, "%.0f", targetCals)
                            + " kcal/day establishes an optimal energy balance to sustainably achieve " + goalName
                            + " without triggering metabolic slowdown or lean tissue breakdown.\n\n")
                    + "HYDRATION & RECOVERY MECHANICS: Daily fluid intake is set at "
                    + String.format(Locale.ROOT, "%.1f", suggestedHydrationLiters)
                    + " L/day (33 mL/kg body mass + activity bonus), ensuring optimal blood volume, joint lubrication, and cellular nutrient transport. When paired with 7-9 hours of dark, temperature-controlled sleep, tissue repair and systemic cortisol regulation remain elevated.";
        }
        data.put("AI_EXPLANATION", aiExplanation);

        // 7. Action Guide & Checklist Parameters (Page 2 Five Categories)
        String recNutritionTitle = "Whole-Food Macronutrient Balance";
        String recNutritionDesc = "Target " + Math.round(targetCals) + " kcal/day emphasizing balanced lean protein, low-glycemic carbohydrates, and essential healthy fats.";
        String recNutritionCheck1 = "Distribute meals across 3-4 structured sittings with " + sittingLow + "-" + sittingHigh + "g protein per meal.";
        String recNutritionCheck2 = "Prioritize minimally processed whole foods: lean proteins, oats, and leafy vegetables.";

        String recExerciseTitle = "Progressive Movement & Strength Protocol";
        String recExerciseDesc = "Execute 3-5 structured training sessions weekly combining progressive resistance and aerobic conditioning.";
        String recExerciseCheck1 = "Complete 3-4 compound resistance sessions focusing on good form and progressive overload.";
        String recExerciseCheck2 = "Incorporate 20-30 minutes of low-impact walking or cardio on active recovery days.";

        String recHydrationTitle = "Optimal Fluid & Electrolyte Balance";
        String recHydrationDesc = String.format(Locale.ROOT, "Maintain %.1f L of clean water daily to support cellular energy, joint lubrication, and focus.", suggestedHydrationLiters);
        String recHydrationCheck1 = "Drink 400-500 mL of room-temperature water immediately upon waking to rehydrate.";
        String recHydrationCheck2 = "Keep a personal water bottle at your workspace and sip consistently between main meals.";

        String recSleepTitle = "Circadian Alignment & Restorative Sleep";
        String recSleepDesc = "Target 7.0 - 9.0 hours of uninterrupted nocturnal sleep for tissue recovery and hormone regulation.";
        String recSleepCheck1 = "Maintain a consistent sleep and wake schedule within 30 minutes every single day.";
        String recSleepCheck2 = "Dim artificial room lights and enforce a screen curfew 45 minutes before bedtime.";

        String recHabitsTitle = "Daily Consistency & Frictionless Micro-Habits";
        String recHabitsDesc = "Anchor small, high-impact routines to established daily environmental triggers.";
        String recHabitsCheck1 = "Get 10 minutes of direct morning sunlight outdoors within an hour of waking.";
        String recHabitsCheck2 = "Take a 5-10 minute gentle walk after lunch and dinner to aid digestion and blood sugar.";

        if (recommendations != null) {
            for (Recommendation r : recommendations) {
                Long cid = r.getCategoryId();
                String titleUpper = (r.getTitle() != null ? r.getTitle() : "").toUpperCase(Locale.ROOT);
                String descUpper = (r.getDescription() != null ? r.getDescription() : "").toUpperCase(Locale.ROOT);

                // Authoritative category routing: categoryId is checked first
                int categoryCode = 0; // 1: Nutrition, 2: Exercise, 3: Sleep, 4: Habits, 7: Hydration
                if (cid != null) {
                    if (cid == 1L || (cid >= 8L && cid <= 14L)) categoryCode = 1;
                    else if (cid == 2L) categoryCode = 2;
                    else if (cid == 3L) categoryCode = 3;
                    else if (cid == 4L) categoryCode = 4;
                    else if (cid == 7L) categoryCode = 7;
                }

                // If not determined by categoryId, fallback to keyword inspection
                if (categoryCode == 0) {
                    // Check Sleep first (so sleep descriptions mentioning protein synthesis are never misrouted to Nutrition)
                    if (titleUpper.contains("SLEEP") || titleUpper.contains("CIRCADIAN") || titleUpper.contains("NOCTURNAL")
                            || (titleUpper.contains("RECOVERY") && !titleUpper.contains("MEAL") && !titleUpper.contains("FUELING"))) {
                        categoryCode = 3;
                    } else if (titleUpper.contains("HYDRATION") || titleUpper.contains("WATER") || titleUpper.contains("FLUID")
                            || titleUpper.contains("ELECTROLYTE")) {
                        categoryCode = 7;
                    } else if (titleUpper.contains("NUTRITION") || titleUpper.contains("MEAL") || titleUpper.contains("DIET")
                            || titleUpper.contains("FUELING") || titleUpper.contains("BREAKFAST") || titleUpper.contains("LUNCH")
                            || titleUpper.contains("DINNER") || titleUpper.contains("FOOD") || titleUpper.contains("PROTEIN")) {
                        categoryCode = 1;
                    } else if (titleUpper.contains("EXERCISE") || titleUpper.contains("WORKOUT") || titleUpper.contains("TRAINING")
                            || titleUpper.contains("FITNESS") || titleUpper.contains("STRENGTH") || titleUpper.contains("RESISTANCE")
                            || titleUpper.contains("MOVEMENT") || titleUpper.contains("CARDIO")) {
                        categoryCode = 2;
                    } else if (titleUpper.contains("HABIT") || titleUpper.contains("ROUTINE") || titleUpper.contains("CONSISTENCY")) {
                        categoryCode = 4;
                    }
                }

                if (categoryCode == 3) {
                    if (r.getTitle() != null && !r.getTitle().isBlank()) recSleepTitle = r.getTitle();
                    if (r.getDescription() != null && !r.getDescription().isBlank()) recSleepDesc = r.getDescription();
                    String[] checks = parseChecklist(r, recSleepCheck1, recSleepCheck2);
                    recSleepCheck1 = checks[0];
                    recSleepCheck2 = checks[1];
                } else if (categoryCode == 7) {
                    if (r.getTitle() != null && !r.getTitle().isBlank()) recHydrationTitle = r.getTitle();
                    if (r.getDescription() != null && !r.getDescription().isBlank()) recHydrationDesc = r.getDescription();
                    String[] checks = parseChecklist(r, recHydrationCheck1, recHydrationCheck2);
                    recHydrationCheck1 = checks[0];
                    recHydrationCheck2 = checks[1];
                } else if (categoryCode == 1) {
                    if (r.getTitle() != null && !r.getTitle().isBlank()) recNutritionTitle = r.getTitle();
                    if (r.getDescription() != null && !r.getDescription().isBlank()) recNutritionDesc = r.getDescription();
                    String[] checks = parseChecklist(r, recNutritionCheck1, recNutritionCheck2);
                    recNutritionCheck1 = checks[0];
                    recNutritionCheck2 = checks[1];
                } else if (categoryCode == 2) {
                    if (r.getTitle() != null && !r.getTitle().isBlank()) recExerciseTitle = r.getTitle();
                    if (r.getDescription() != null && !r.getDescription().isBlank()) recExerciseDesc = r.getDescription();
                    String[] checks = parseChecklist(r, recExerciseCheck1, recExerciseCheck2);
                    recExerciseCheck1 = checks[0];
                    recExerciseCheck2 = checks[1];
                } else if (categoryCode == 4) {
                    if (r.getTitle() != null && !r.getTitle().isBlank()) recHabitsTitle = r.getTitle();
                    if (r.getDescription() != null && !r.getDescription().isBlank()) recHabitsDesc = r.getDescription();
                    String[] checks = parseChecklist(r, recHabitsCheck1, recHabitsCheck2);
                    recHabitsCheck1 = checks[0];
                    recHabitsCheck2 = checks[1];
                }
            }
        }

        data.put("recNutritionTitle", recNutritionTitle);
        data.put("recNutritionDesc", recNutritionDesc);
        data.put("recNutritionCheck1", recNutritionCheck1);
        data.put("recNutritionCheck2", recNutritionCheck2);

        data.put("recExerciseTitle", recExerciseTitle);
        data.put("recExerciseDesc", recExerciseDesc);
        data.put("recExerciseCheck1", recExerciseCheck1);
        data.put("recExerciseCheck2", recExerciseCheck2);

        data.put("recHydrationTitle", recHydrationTitle);
        data.put("recHydrationDesc", recHydrationDesc);
        data.put("recHydrationCheck1", recHydrationCheck1);
        data.put("recHydrationCheck2", recHydrationCheck2);

        data.put("recSleepTitle", recSleepTitle);
        data.put("recSleepDesc", recSleepDesc);
        data.put("recSleepCheck1", recSleepCheck1);
        data.put("recSleepCheck2", recSleepCheck2);

        data.put("recHabitsTitle", recHabitsTitle);
        data.put("recHabitsDesc", recHabitsDesc);
        data.put("recHabitsCheck1", recHabitsCheck1);
        data.put("recHabitsCheck2", recHabitsCheck2);

        data.put("recommendationCount", recommendations != null ? recommendations.size() : 0);

        return data;
    }

    private static String[] parseChecklist(Recommendation r, String default1, String default2) {
        if (r.getRecommendedActions() != null && !r.getRecommendedActions().isBlank()) {
            String actions = r.getRecommendedActions().trim();
            if (actions.contains(";")) {
                String[] parts = actions.split(";", 2);
                return new String[]{parts[0].trim(), parts[1].trim()};
            } else if (r.getSuggestedTarget() != null && !r.getSuggestedTarget().isBlank()) {
                return new String[]{actions, "Suggested Target: " + r.getSuggestedTarget().trim()};
            } else {
                return new String[]{actions, default2};
            }
        }
        return new String[]{default1, default2};
    }
}