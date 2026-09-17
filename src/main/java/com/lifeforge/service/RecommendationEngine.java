package com.lifeforge.service;

import com.lifeforge.dao.RecommendationDao;
import com.lifeforge.model.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Rule-Based Recommendation Engine for LIFEForge.
 *
 * This engine is the authoritative SOURCE OF TRUTH for:
 * 1. Base recommendation matching from the database
 * 2. Deterministic priority resolution across recommendation categories
 * 3. Explainable Match Score calculation (40% completeness, 40% goal alignment, 20% activity synergy)
 * 4. Academic calculation trace generation
 * 5. Unified Daily Blueprint generation
 */
public class RecommendationEngine {

    private final RecommendationDao recommendationDao;
    private final RecommendationPriorityResolver priorityResolver;

    public RecommendationEngine(RecommendationDao recommendationDao) {
        this(recommendationDao, new RecommendationPriorityResolver());
    }

    public RecommendationEngine(RecommendationDao recommendationDao, RecommendationPriorityResolver priorityResolver) {
        this.recommendationDao = recommendationDao;
        this.priorityResolver = priorityResolver;
    }

    public Optional<Recommendation> generateBaseRecommendation(
            User user,
            Goal goal,
            Long categoryId
    ) throws SQLException {

        if (user == null || goal == null || categoryId == null) {
            return Optional.empty();
        }

        ActivityLevel activityLevel = user.getActivityLevel();

        return recommendationDao.findBestMatch(
                goal.getId(),
                categoryId,
                activityLevel
        );
    }

    public RecommendationPriority resolvePriority(Goal goal, ActivityLevel activityLevel, RecommendationCategory category) {
        return priorityResolver.resolve(goal, activityLevel, category);
    }

    public List<String> getPrimaryFocusAreas(Goal goal) {
        return priorityResolver.getPrimaryFocusAreas(goal);
    }

    public List<String> getSupportingAreas(Goal goal) {
        return priorityResolver.getSupportingAreas(goal);
    }

    public String getPersonalizedFocusNarrative(Goal goal, User user) {
        return priorityResolver.getPersonalizedFocusNarrative(goal, user);
    }

    public MatchScoreBreakdown computeMatchScore(User user, Goal goal) {
        boolean hasGoalRecs = false;
        boolean hasActivityRec = false;
        if (goal != null && goal.getId() != null) {
            try {
                hasGoalRecs = recommendationDao.hasRecommendationsForGoal(goal.getId());
                if (user != null && user.getActivityLevel() != null) {
                    hasActivityRec = recommendationDao.hasActivitySpecificRecommendation(goal.getId(), user.getActivityLevel());
                }
            } catch (SQLException ignored) {
                hasGoalRecs = true;
            }
        }
        return MatchScoreBreakdown.compute(user, goal, hasGoalRecs, hasActivityRec);
    }

    public CalculationTrace buildCalculationTrace(User user, Goal goal, CalorieService calorieService) {
        MatchScoreBreakdown score = computeMatchScore(user, goal);
        return new CalculationTrace(user, goal, calorieService, score);
    }

    public DailyBlueprint buildDailyBlueprint(User user, Goal goal) {
        ActivityLevel activity = user != null && user.getActivityLevel() != null
                ? user.getActivityLevel()
                : ActivityLevel.SEDENTARY;

        DailyBlueprint blueprint = new DailyBlueprint(goal, activity);
        String goalCode = goal != null && goal.getCode() != null ? goal.getCode().toUpperCase() : "";

        // Morning
        blueprint.addMorning("\uD83D\uDCA7", "Hydration", // 💧
                "Drink 400\u2013500 mL of room-temperature water upon waking to kickstart hydration and metabolism.");
        if (goalCode.contains("MUSCLE")) {
            blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                    "Include a protein-rich food source with each meal (e.g. eggs, Greek yogurt) paired with complex carbohydrates.");
        } else if (goalCode.contains("WEIGHT")) {
            blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                    "Choose a balanced, lower-calorie breakfast emphasizing lean protein and high fiber for satiety.");
        } else if (goalCode.contains("SKIN")) {
            blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                    "Include antioxidant-rich foods, healthy fats, and hydrating fruits with breakfast.");
        } else {
            blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                    "Enjoy a nourishing, balanced breakfast to provide steady morning energy.");
        }
        blueprint.addMorning("\uD83C\uDFC3", "Activity", // 🏃
                "10\u201315 minutes of light morning mobility, dynamic stretching, or an easy wake-up walk.");

        // Midday
        blueprint.addMidday("\uD83C\uDF7D", "Nutrition", // 🍽
                "Follow a balanced lunch structure with lean protein, colorful vegetables, and portion control.");
        if (goalCode.contains("MUSCLE")) {
            blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                    "Execute scheduled resistance training focusing on progressive overload and compound lifts.");
        } else if (goalCode.contains("FITNESS") || goalCode.contains("WEIGHT")) {
            blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                    "Complete a scheduled workout or brisk 25\u201330 minute walk to elevate heart rate safely.");
        } else if (goalCode.contains("SLEEP")) {
            blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                    "Engage in moderate daytime physical activity to reinforce natural circadian drive.");
        } else {
            blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                    "Maintain structured physical movement appropriate for your current activity level.");
        }
        blueprint.addMidday("\uD83D\uDCA7", "Hydration", // 💧
                "Sip water steadily through midday hours to avoid afternoon fatigue and dehydration.");

        // Evening
        blueprint.addEvening("\uD83C\uDF7D", "Dinner", // 🍽
                "Enjoy a lighter, wholesome dinner 2\u20133 hours before bed; avoid heavy refined sugars.");
        blueprint.addEvening("\uD83E\uDDD8", "Recovery", // 🧘
                "Dim lights 60 minutes before bed, limit blue-light screens, and allow mental decompression.");
        blueprint.addEvening("\uD83D\uDE34", "Sleep", // 😴
                "Target 7\u20139 hours of uninterrupted restorative sleep in a cool, quiet, dark environment.");

        return blueprint;
    }
}