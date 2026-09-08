package com.lifeforge.service;

import com.lifeforge.dao.SavedRecommendationDao;
import com.lifeforge.model.SavedRecommendation;

import java.sql.SQLException;
import java.util.List;

/**
 * Business logic for saving/viewing/deleting a user's saved
 * recommendations, with duplicate-save prevention.
 */
public class SavedRecommendationService {

    private final SavedRecommendationDao savedRecommendationDao;

    public SavedRecommendationService(SavedRecommendationDao savedRecommendationDao) {
        this.savedRecommendationDao = savedRecommendationDao;
    }

    public static class SaveResult {
        public final boolean success;
        public final String message;

        private SaveResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static SaveResult ok(String message) {
            return new SaveResult(true, message);
        }

        public static SaveResult fail(String message) {
            return new SaveResult(false, message);
        }
    }

    public SaveResult saveRecommendation(Long userId, Long recommendationId) {
        try {
            if (savedRecommendationDao.alreadySaved(userId, recommendationId)) {
                return SaveResult.fail("You have already saved this recommendation.");
            }
            savedRecommendationDao.save(userId, recommendationId);
            return SaveResult.ok("Recommendation saved successfully.");
        } catch (SQLException e) {
            return SaveResult.fail("Failed to save recommendation due to a database error.");
        }
    }

    public List<SavedRecommendation> listSavedForUser(Long userId) throws SQLException {
        return savedRecommendationDao.findByUser(userId);
    }

    public SaveResult deleteSaved(Long savedRecommendationId, Long userId) {
        try {
            savedRecommendationDao.delete(savedRecommendationId, userId);
            return SaveResult.ok("Saved recommendation deleted.");
        } catch (SQLException e) {
            return SaveResult.fail("Failed to delete saved recommendation due to a database error.");
        }
    }
}