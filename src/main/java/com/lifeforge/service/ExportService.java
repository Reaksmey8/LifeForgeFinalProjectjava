package com.lifeforge.service;

import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the data map that JasperReportsService feeds into the
 * LifeForgeHealthReport.jrxml template. This is a recommendation
 * report (profile + goal + calculations + recommendations) - it
 * must never become a daily tracking/progress report.
 */
public class ExportService {

    private final CalorieService calorieService;
    private final HydrationService hydrationService;

    public ExportService(CalorieService calorieService, HydrationService hydrationService) {
        this.calorieService = calorieService;
        this.hydrationService = hydrationService;
    }

    public Map<String, Object> buildReportData(User user, Goal goal, List<Recommendation> recommendations) {
        Map<String, Object> data = new HashMap<>();

        data.put("fullName", user.getFullName());
        data.put("email", user.getEmail());
        data.put("age", user.getAge());
        data.put("gender", user.getGender().name());
        data.put("heightCm", user.getHeightCm());
        data.put("weightKg", user.getWeightKg());
        data.put("activityLevel", user.getActivityLevel().getDescription());
        data.put("goalName", goal.getName());

        CalorieService.CalorieSummary calorieSummary = calorieService.calculateFor(user, goal);
        data.put("bmr", calorieSummary.bmr);
        data.put("tdee", calorieSummary.tdee);
        data.put("suggestedCalorieTarget", calorieSummary.suggestedTarget);
        data.put("suggestedHydrationLiters", hydrationService.suggestedLitersPerDay(user));

        data.put("recommendationCount", recommendations.size());

        return data;
    }
}