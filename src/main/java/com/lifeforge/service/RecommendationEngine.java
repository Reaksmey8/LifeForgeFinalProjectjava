package com.lifeforge.service;

import com.lifeforge.dao.RecommendationDao;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.User;

import java.sql.SQLException;
import java.util.Optional;

public class RecommendationEngine {

    private final RecommendationDao recommendationDao;

    public RecommendationEngine(RecommendationDao recommendationDao) {
        this.recommendationDao = recommendationDao;
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

        System.out.println("======================================");
        System.out.println("[DEBUG] Recommendation Request");
        System.out.println("[DEBUG] Goal ID      : " + goal.getId());
        System.out.println("[DEBUG] Goal Name    : " + goal.getName());
        System.out.println("[DEBUG] Category ID  : " + categoryId);
        System.out.println("[DEBUG] Activity     : " + activityLevel);
        System.out.println("======================================");

        return recommendationDao.findBestMatch(
                goal.getId(),
                categoryId,
                activityLevel
        );
    }
}