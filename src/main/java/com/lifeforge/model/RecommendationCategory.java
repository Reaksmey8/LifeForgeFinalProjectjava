package com.lifeforge.model;

/**
 * A category of recommendations shown in the Recommendation Hub,
 * e.g. Nutrition, Exercise, Sleep & Recovery, Daily Micro-Habits.
 * A category may optionally have a parent category to support
 * sub-menus (e.g. Nutrition -> Breakfast).
 */
public class RecommendationCategory {

    private Long id;
    private String name;
    private String description;
    private Long parentCategoryId;
    private int displayOrder;

    public RecommendationCategory() {
    }

    public RecommendationCategory(Long id, String name, String description,
                                  Long parentCategoryId, int displayOrder) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.parentCategoryId = parentCategoryId;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getParentCategoryId() {
        return parentCategoryId;
    }

    public void setParentCategoryId(Long parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
    }

    public boolean isTopLevel() {
        return parentCategoryId == null;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    @Override
    public String toString() {
        return name;
    }
}