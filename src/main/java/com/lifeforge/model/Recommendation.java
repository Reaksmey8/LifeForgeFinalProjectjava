package com.lifeforge.model;

/**
 * Recommendation content managed by the LifeForge Recommendation CMS.
 *
 * Database columns:
 * id
 * goal_id
 * category_id
 * activity_level
 * title
 * description
 * recommended_actions
 * suggested_target
 * examples
 * important_notes
 */
public class Recommendation {

    private Long id;
    private Long goalId;
    private Long categoryId;
    private ActivityLevel activityLevel;

    private String title;
    private String description;
    private String recommendedActions;
    private String suggestedTarget;
    private String examples;
    private String importantNotes;

    public Recommendation() {
    }

    public Recommendation(
            Long id,
            Long goalId,
            Long categoryId,
            ActivityLevel activityLevel,
            String title,
            String description,
            String recommendedActions,
            String suggestedTarget,
            String examples,
            String importantNotes
    ) {
        this.id = id;
        this.goalId = goalId;
        this.categoryId = categoryId;
        this.activityLevel = activityLevel;
        this.title = title;
        this.description = description;
        this.recommendedActions = recommendedActions;
        this.suggestedTarget = suggestedTarget;
        this.examples = examples;
        this.importantNotes = importantNotes;
    }

    // ID

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // GOAL ID

    public Long getGoalId() {
        return goalId;
    }

    public void setGoalId(Long goalId) {
        this.goalId = goalId;
    }

    // CATEGORY ID

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    // ACTIVITY LEVEL

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(ActivityLevel activityLevel) {
        this.activityLevel = activityLevel;
    }

    // TITLE

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    // DESCRIPTION

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // RECOMMENDED ACTIONS

    public String getRecommendedActions() {
        return recommendedActions;
    }

    public void setRecommendedActions(String recommendedActions) {
        this.recommendedActions = recommendedActions;
    }

    // SUGGESTED TARGET

    public String getSuggestedTarget() {
        return suggestedTarget;
    }

    public void setSuggestedTarget(String suggestedTarget) {
        this.suggestedTarget = suggestedTarget;
    }

    // EXAMPLES

    public String getExamples() {
        return examples;
    }

    public void setExamples(String examples) {
        this.examples = examples;
    }

    // IMPORTANT NOTES

    public String getImportantNotes() {
        return importantNotes;
    }

    public void setImportantNotes(String importantNotes) {
        this.importantNotes = importantNotes;
    }

    @Override
    public String toString() {
        return title;
    }
}