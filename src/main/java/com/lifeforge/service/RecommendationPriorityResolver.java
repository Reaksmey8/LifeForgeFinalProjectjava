package com.lifeforge.service;

import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Goal;
import com.lifeforge.model.RecommendationCategory;
import com.lifeforge.model.RecommendationPriority;
import com.lifeforge.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic priority resolver that maps Goal + Activity Level + Category
 * to a concrete RecommendationPriority (HIGH, RECOMMENDED, SUPPORTING).
 *
 * This keeps priority rules separate from RecommendationEngine while maintaining
 * pure, reproducible, and explainable rule-based resolution.
 */
public class RecommendationPriorityResolver {

    public RecommendationPriority resolve(Goal goal, ActivityLevel activityLevel, RecommendationCategory category) {
        if (goal == null || category == null) {
            return RecommendationPriority.RECOMMENDED;
        }

        String goalCode = normalizeUpper(goal.getCode());
        String catName = normalizeLower(category.getName());

        return switch (goalCode) {
            case "LOSE_WEIGHT" -> resolveLoseWeight(catName, activityLevel);
            case "GAIN_WEIGHT" -> resolveGainWeight(catName, activityLevel);
            case "BUILD_MUSCLE" -> resolveBuildMuscle(catName, activityLevel);
            case "IMPROVE_FITNESS" -> resolveImproveFitness(catName, activityLevel);
            case "SKIN_HEALTH", "IMPROVE_SKIN_HEALTH" -> resolveSkinHealth(catName, activityLevel);
            case "IMPROVE_SLEEP" -> resolveImproveSleep(catName, activityLevel);
            case "POSTURE_CORRECTION" -> resolvePostureCorrection(catName, activityLevel);
            case "GENERAL_WELLNESS" -> resolveGeneralWellness(catName, activityLevel);
            default -> resolveDefault(catName);
        };
    }

    private boolean isPhysicalCategory(String catName) {
        return catName.contains("exercise") || catName.contains("fitness") || catName.contains("workout");
    }

    private RecommendationPriority resolveLoseWeight(String catName, ActivityLevel activityLevel) {
        if (catName.contains("nutrition")) {
            return RecommendationPriority.HIGH;
        }
        if (isPhysicalCategory(catName)) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("hydration") || catName.contains("sleep")) {
            return RecommendationPriority.RECOMMENDED;
        }
        return RecommendationPriority.SUPPORTING;
    }

    private RecommendationPriority resolveGainWeight(String catName, ActivityLevel activityLevel) {
        if (catName.contains("nutrition")) {
            return RecommendationPriority.HIGH;
        }
        if (isPhysicalCategory(catName)) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("hydration") || catName.contains("sleep")) {
            return RecommendationPriority.RECOMMENDED;
        }
        return RecommendationPriority.SUPPORTING;
    }

    private RecommendationPriority resolveBuildMuscle(String catName, ActivityLevel activityLevel) {
        if (isPhysicalCategory(catName)) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("nutrition")) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("sleep") || catName.contains("hydration")) {
            return RecommendationPriority.RECOMMENDED;
        }
        return RecommendationPriority.SUPPORTING;
    }

    private RecommendationPriority resolveImproveFitness(String catName, ActivityLevel activityLevel) {
        if (isPhysicalCategory(catName)) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("nutrition") || catName.contains("hydration") || catName.contains("sleep")) {
            return RecommendationPriority.RECOMMENDED;
        }
        return RecommendationPriority.SUPPORTING;
    }

    private RecommendationPriority resolveSkinHealth(String catName, ActivityLevel activityLevel) {
        if (catName.contains("hydration")) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("nutrition")) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("sleep")) {
            return RecommendationPriority.RECOMMENDED;
        }
        return RecommendationPriority.SUPPORTING;
    }

    private RecommendationPriority resolveImproveSleep(String catName, ActivityLevel activityLevel) {
        if (catName.contains("sleep")) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("habit")) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("hydration")) {
            return RecommendationPriority.RECOMMENDED;
        }
        return RecommendationPriority.SUPPORTING;
    }

    private RecommendationPriority resolvePostureCorrection(String catName, ActivityLevel activityLevel) {
        if (isPhysicalCategory(catName)) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("habit")) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("sleep")) {
            return RecommendationPriority.RECOMMENDED;
        }
        return RecommendationPriority.SUPPORTING;
    }

    private RecommendationPriority resolveGeneralWellness(String catName, ActivityLevel activityLevel) {
        if (catName.contains("nutrition")) {
            return RecommendationPriority.HIGH;
        }
        if (isPhysicalCategory(catName) || catName.contains("hydration") || catName.contains("sleep")) {
            return RecommendationPriority.RECOMMENDED;
        }
        return RecommendationPriority.SUPPORTING;
    }

    private RecommendationPriority resolveDefault(String catName) {
        if (catName.contains("nutrition") || isPhysicalCategory(catName)) {
            return RecommendationPriority.HIGH;
        }
        if (catName.contains("hydration") || catName.contains("sleep")) {
            return RecommendationPriority.RECOMMENDED;
        }
        return RecommendationPriority.SUPPORTING;
    }

    public List<String> getPrimaryFocusAreas(Goal goal) {
        List<String> areas = new ArrayList<>();
        if (goal == null || goal.getCode() == null) {
            areas.add("Nutrition");
            areas.add("Exercise");
            return areas;
        }
        String code = normalizeUpper(goal.getCode());
        switch (code) {
            case "LOSE_WEIGHT", "GAIN_WEIGHT" -> {
                areas.add("Nutrition");
                areas.add("Exercise");
            }
            case "BUILD_MUSCLE" -> {
                areas.add("Exercise");
                areas.add("Nutrition");
            }
            case "IMPROVE_FITNESS" -> {
                areas.add("Exercise");
            }
            case "SKIN_HEALTH", "IMPROVE_SKIN_HEALTH" -> {
                areas.add("Hydration");
                areas.add("Nutrition");
            }
            case "IMPROVE_SLEEP" -> {
                areas.add("Sleep & Recovery");
                areas.add("Daily Micro-Habits");
            }
            case "POSTURE_CORRECTION" -> {
                areas.add("Exercise");
                areas.add("Daily Micro-Habits");
            }
            default -> {
                areas.add("Nutrition");
                areas.add("Exercise");
            }
        }
        return areas;
    }

    public List<String> getSupportingAreas(Goal goal) {
        List<String> areas = new ArrayList<>();
        if (goal == null || goal.getCode() == null) {
            areas.add("Hydration");
            areas.add("Sleep & Recovery");
            return areas;
        }
        String code = normalizeUpper(goal.getCode());
        switch (code) {
            case "LOSE_WEIGHT", "GAIN_WEIGHT", "BUILD_MUSCLE" -> {
                areas.add("Hydration");
                areas.add("Sleep & Recovery");
            }
            case "IMPROVE_FITNESS" -> {
                areas.add("Nutrition");
                areas.add("Hydration");
                areas.add("Sleep & Recovery");
            }
            case "SKIN_HEALTH", "IMPROVE_SKIN_HEALTH" -> {
                areas.add("Sleep & Recovery");
                areas.add("Daily Micro-Habits");
            }
            case "IMPROVE_SLEEP" -> {
                areas.add("Hydration");
                areas.add("Exercise");
            }
            case "POSTURE_CORRECTION" -> {
                areas.add("Sleep & Recovery");
                areas.add("Hydration");
            }
            default -> {
                areas.add("Hydration");
                areas.add("Sleep & Recovery");
            }
        }
        return areas;
    }

    public String getPersonalizedFocusNarrative(Goal goal, User user) {
        String goalCode = goal == null || goal.getCode() == null ? "" : normalizeUpper(goal.getCode());
        String activity = user == null || user.getActivityLevel() == null
                ? "current"
                : user.getActivityLevel().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        return switch (goalCode) {
            case "LOSE_WEIGHT" -> {
                if (age >= 50) {
                    yield "Focus on preserving lean muscle with high-protein nutrition, joint-friendly low-impact exercise, "
                            + "proactive hydration, and consistent restorative rest calibrated for age " + age + " and " + activity + " activity.";
                } else if (age < 35) {
                    yield "Focus on sustainable caloric deficit, higher training volume, protein-forward satiety, "
                            + "and metabolic conditioning calibrated for age " + age + " and " + activity + " activity.";
                } else {
                    yield "Focus on sustainable nutrition, appropriate exercise, "
                            + "hydration, and consistent sleep calibrated for age " + age + " and " + activity + " activity.";
                }
            }
            case "GAIN_WEIGHT" -> {
                if (age >= 50) {
                    yield "Focus on nutrient-dense calorie surplus, joint-conscious progressive strength training, "
                            + "and extended tissue recovery calibrated for age " + age + " and " + activity + " activity.";
                } else {
                    yield "Focus on calorie surplus nutrition, progressive exercise, "
                            + "and proper recovery calibrated for age " + age + " and " + activity + " activity.";
                }
            }
            case "BUILD_MUSCLE" -> {
                if (age >= 50) {
                    yield "Focus on joint-friendly resistance training (8–15 reps), higher per-meal protein (35–40g+ to overcome anabolic resistance), "
                            + "proactive scheduled hydration, and extended recovery (48–72h) calibrated for age " + age + " and " + activity + " activity.";
                } else if (age < 35) {
                    yield "Focus on progressive resistance training (6–10 reps), protein-forward nutrition (~25–30g/meal), "
                            + "workout hydration, and recovery calibrated for age " + age + " and " + activity + " activity.";
                } else {
                    yield "Focus on progressive resistance training, protein-forward nutrition, "
                            + "and recovery calibrated for age " + age + " and " + activity + " activity.";
                }
            }
            case "IMPROVE_FITNESS" -> {
                if (age >= 50) {
                    yield "Focus on joint-friendly cardiovascular conditioning, mobility prep, "
                            + "and supportive daytime hydration calibrated for age " + age + " and " + activity + " activity.";
                } else {
                    yield "Focus on cardiovascular conditioning, functional mobility, "
                            + "and supportive hydration calibrated for age " + age + " and " + activity + " activity.";
                }
            }
            case "SKIN_HEALTH", "IMPROVE_SKIN_HEALTH" ->
                    "Focus on cellular hydration, antioxidant-rich nutrition, "
                            + "and restful sleep suited to age " + age + " and your lifestyle.";
            case "IMPROVE_SLEEP" ->
                    "Focus on evening wind-down rituals, consistent sleep schedule, "
                            + "and daily micro-habits suited to age " + age + " and your lifestyle.";
            case "POSTURE_CORRECTION" ->
                    "Focus on postural realignment exercises, ergonomic adjustments, "
                            + "and consistent daily micro-habits suited to age " + age + ".";
            default ->
                    "Focus on balanced nutrition, regular physical movement, "
                            + "adequate hydration, and consistent restorative rest calibrated for age " + age + ".";
        };
    }

    private String normalizeUpper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeLower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
