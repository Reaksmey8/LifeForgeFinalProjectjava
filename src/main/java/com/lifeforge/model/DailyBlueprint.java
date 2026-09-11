package com.lifeforge.model;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory recommendation-only model representing a healthy recommended daily rhythm.
 * It contains ZERO tracking, checkboxes, streaks, or logging mechanisms.
 */
public class DailyBlueprint {

    public record BlueprintItem(String emoji, String title, String guidance) {}

    private final Goal goal;
    private final ActivityLevel activityLevel;
    private final List<BlueprintItem> morning = new ArrayList<>();
    private final List<BlueprintItem> midday = new ArrayList<>();
    private final List<BlueprintItem> evening = new ArrayList<>();

    public DailyBlueprint(Goal goal, ActivityLevel activityLevel) {
        this.goal = goal;
        this.activityLevel = activityLevel;
    }

    public void addMorning(String emoji, String title, String guidance) {
        morning.add(new BlueprintItem(emoji, title, guidance));
    }

    public void addMidday(String emoji, String title, String guidance) {
        midday.add(new BlueprintItem(emoji, title, guidance));
    }

    public void addEvening(String emoji, String title, String guidance) {
        evening.add(new BlueprintItem(emoji, title, guidance));
    }

    public Goal getGoal() {
        return goal;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public List<BlueprintItem> getMorning() {
        return morning;
    }

    public List<BlueprintItem> getMidday() {
        return midday;
    }

    public List<BlueprintItem> getEvening() {
        return evening;
    }
}
