package com.lifeforge.service;

import com.lifeforge.dao.GoalDao;
import com.lifeforge.dao.RecommendationDao;
import com.lifeforge.dao.SavedRecommendationDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.dao.UserGoalDao;
import com.lifeforge.model.Goal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Produces admin analytics figures. Every number here is backed by
 * an actual database query - nothing here is fabricated or estimated.
 */
public class AnalyticsService {

    private final UserDao userDao;
    private final UserGoalDao userGoalDao;
    private final GoalDao goalDao;
    private final RecommendationDao recommendationDao;
    private final SavedRecommendationDao savedRecommendationDao;

    public AnalyticsService(UserDao userDao, UserGoalDao userGoalDao, GoalDao goalDao,
                            RecommendationDao recommendationDao, SavedRecommendationDao savedRecommendationDao) {
        this.userDao = userDao;
        this.userGoalDao = userGoalDao;
        this.goalDao = goalDao;
        this.recommendationDao = recommendationDao;
        this.savedRecommendationDao = savedRecommendationDao;
    }

    public static class GoalDistributionEntry {
        public final String goalName;
        public final long userCount;

        public GoalDistributionEntry(String goalName, long userCount) {
            this.goalName = goalName;
            this.userCount = userCount;
        }
    }

    public static class CategoryPopularityEntry {
        public final String categoryName;
        public final long saveCount;

        public CategoryPopularityEntry(String categoryName, long saveCount) {
            this.categoryName = categoryName;
            this.saveCount = saveCount;
        }
    }

    public static class AnalyticsSummary {
        public final long totalUsers;
        public final long activeUsers;
        public final long totalRecommendations;
        public final long totalSavedRecommendations;
        public final List<GoalDistributionEntry> goalDistribution;
        public final List<CategoryPopularityEntry> popularCategories;

        public AnalyticsSummary(long totalUsers, long activeUsers, long totalRecommendations,
                                long totalSavedRecommendations, List<GoalDistributionEntry> goalDistribution,
                                List<CategoryPopularityEntry> popularCategories) {
            this.totalUsers = totalUsers;
            this.activeUsers = activeUsers;
            this.totalRecommendations = totalRecommendations;
            this.totalSavedRecommendations = totalSavedRecommendations;
            this.goalDistribution = goalDistribution;
            this.popularCategories = popularCategories;
        }
    }

    public AnalyticsSummary buildSummary() throws SQLException {
        long totalUsers = userDao.countAll();
        long activeUsers = userDao.countActiveUsers();
        long totalRecommendations = recommendationDao.countAll();
        long totalSaved = savedRecommendationDao.countAll();

        List<GoalDistributionEntry> goalDistribution = new ArrayList<>();
        for (Object[] row : userGoalDao.countUsersPerActiveGoal()) {
            Long goalId = (Long) row[0];
            Long count = (Long) row[1];
            Optional<Goal> goal = goalDao.findById(goalId);
            goalDistribution.add(new GoalDistributionEntry(
                    goal.map(Goal::getName).orElse("Unknown Goal"), count));
        }

        List<CategoryPopularityEntry> popularCategories = new ArrayList<>();
        for (Object[] row : recommendationDao.findPopularCategories(5)) {
            popularCategories.add(new CategoryPopularityEntry((String) row[0], (Long) row[1]));
        }

        return new AnalyticsSummary(totalUsers, activeUsers, totalRecommendations, totalSaved,
                goalDistribution, popularCategories);
    }
}