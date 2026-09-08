package com.lifeforge.service;

import com.lifeforge.dao.GoalDao;
import com.lifeforge.dao.UserGoalDao;
import com.lifeforge.model.Goal;
import com.lifeforge.model.UserGoal;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for goal listing and per-user goal selection.
 */
public class GoalService {

    private final GoalDao goalDao;
    private final UserGoalDao userGoalDao;

    public GoalService(GoalDao goalDao, UserGoalDao userGoalDao) {
        this.goalDao = goalDao;
        this.userGoalDao = userGoalDao;
    }

    public List<Goal> listActiveGoals() throws SQLException {
        return goalDao.findAllActive();
    }

    public List<Goal> listAllGoals() throws SQLException {
        return goalDao.findAll();
    }

    public Optional<Goal> findById(Long goalId) throws SQLException {
        return goalDao.findById(goalId);
    }

    public Optional<Goal> getCurrentGoalForUser(Long userId) throws SQLException {
        Optional<UserGoal> activeUserGoal = userGoalDao.findActiveGoalForUser(userId);
        if (activeUserGoal.isEmpty()) {
            return Optional.empty();
        }
        return goalDao.findById(activeUserGoal.get().getGoalId());
    }

    public UserGoal selectGoal(Long userId, Long goalId) throws SQLException {
        return userGoalDao.selectGoal(userId, goalId);
    }

    public Goal createGoal(String code, String name, String description) throws SQLException {
        Goal goal = new Goal(null, code.trim().toUpperCase().replace(" ", "_"), name.trim(), description, true);
        return goalDao.create(goal);
    }

    public void updateGoal(Goal goal) throws SQLException {
        goalDao.update(goal);
    }

    public void deleteGoal(Long goalId) throws SQLException {
        goalDao.delete(goalId);
    }
}