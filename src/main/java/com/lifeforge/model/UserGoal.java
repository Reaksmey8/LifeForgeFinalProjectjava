package com.lifeforge.model;

import java.time.LocalDateTime;

/**
 * Records which goal a user currently has selected.
 * A user has at most one active UserGoal at a time; selecting a
 * new goal deactivates the previous one (history is preserved).
 */
public class UserGoal {

    private Long id;
    private Long userId;
    private Long goalId;
    private boolean active;
    private LocalDateTime selectedAt;

    public UserGoal() {
    }

    public UserGoal(Long id, Long userId, Long goalId, boolean active, LocalDateTime selectedAt) {
        this.id = id;
        this.userId = userId;
        this.goalId = goalId;
        this.active = active;
        this.selectedAt = selectedAt;
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

    public Long getGoalId() {
        return goalId;
    }

    public void setGoalId(Long goalId) {
        this.goalId = goalId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getSelectedAt() {
        return selectedAt;
    }

    public void setSelectedAt(LocalDateTime selectedAt) {
        this.selectedAt = selectedAt;
    }
}