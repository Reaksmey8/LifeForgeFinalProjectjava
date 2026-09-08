package com.lifeforge.model;

import java.time.LocalDateTime;

/**
 * A recommendation a user has explicitly chosen to save for
 * later reference. Uniqueness of (userId, recommendationId) is
 * enforced at the database level to prevent accidental duplicates.
 */
public class SavedRecommendation {

    private Long id;
    private Long userId;
    private Long recommendationId;
    private LocalDateTime savedAt;

    public SavedRecommendation() {
    }

    public SavedRecommendation(Long id, Long userId, Long recommendationId, LocalDateTime savedAt) {
        this.id = id;
        this.userId = userId;
        this.recommendationId = recommendationId;
        this.savedAt = savedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRecommendationId() {
        return recommendationId;
    }

    public void setRecommendationId(Long recommendationId) {
        this.recommendationId = recommendationId;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(LocalDateTime savedAt) {
        this.savedAt = savedAt;
    }
}