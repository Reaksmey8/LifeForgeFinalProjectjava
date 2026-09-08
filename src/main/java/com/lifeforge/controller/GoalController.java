package com.lifeforge.controller;

import com.lifeforge.Session;
import com.lifeforge.model.Goal;
import com.lifeforge.model.User;
import com.lifeforge.service.GoalService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Goal listing and per-user goal selection. A user must have a
 * selected goal before the recommendation flow can run.
 */
public class GoalController extends BaseController {

    private final GoalService goalService;
    private final Session session;

    public GoalController(GoalService goalService, Session session) {
        this.goalService = goalService;
        this.session = session;
    }

    public List<Goal> listActiveGoals() {
        try {
            return goalService.listActiveGoals();
        } catch (SQLException e) {
            setError(e, "Failed to load goals.");
            return null;
        }
    }

    public List<Goal> listAllGoals() {
        try {
            return goalService.listAllGoals();
        } catch (SQLException e) {
            setError(e, "Failed to load goals.");
            return null;
        }
    }

    public Optional<Goal> getCurrentGoal() {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return Optional.empty();
        }
        try {
            return goalService.getCurrentGoalForUser(current.getId());
        } catch (SQLException e) {
            setError(e, "Failed to load your current goal.");
            return Optional.empty();
        }
    }

    public boolean selectGoal(Long goalId) {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return false;
        }
        try {
            goalService.selectGoal(current.getId(), goalId);
            return true;
        } catch (SQLException e) {
            setError(e, "Failed to select this goal.");
            return false;
        }
    }

    public Goal createGoal(String code, String name, String description) {
        try {
            return goalService.createGoal(code, name, description);
        } catch (SQLException e) {
            setError(e, "Failed to create the goal.");
            return null;
        }
    }

    public boolean updateGoal(Goal goal) {
        try {
            goalService.updateGoal(goal);
            return true;
        } catch (SQLException e) {
            setError(e, "Failed to update the goal.");
            return false;
        }
    }

    public boolean deleteGoal(Long goalId) {
        try {
            goalService.deleteGoal(goalId);
            return true;
        } catch (SQLException e) {
            setError(e, "Failed to delete the goal.");
            return false;
        }
    }
}