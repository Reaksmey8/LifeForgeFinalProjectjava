package com.lifeforge.service;

import com.lifeforge.dao.SavedRecommendationDao;
import com.lifeforge.model.SavedRecommendation;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

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

    public boolean isAlreadySaved(Long userId, Long recommendationId) {
        try {
            return savedRecommendationDao.alreadySaved(userId, recommendationId);
        } catch (SQLException e) {
            return false;
        }
    }

    public SaveResult saveRecommendation(Long userId, Long recommendationId) {
        try {
            Optional<SavedRecommendation> existingInCat = Optional.empty();
            try {
                existingInCat = savedRecommendationDao.findSavedBySameCategory(userId, recommendationId);
            } catch (Exception ignored) {
                // If DB query fails or in mock environment, fallback to alreadySaved check
            }

            if (existingInCat.isPresent()) {
                SavedRecommendation existing = existingInCat.get();
                savedRecommendationDao.updateSavedRecommendation(existing.getId(), recommendationId);
                return SaveResult.ok("Recommendation saved to your list. *");
            }

            if (savedRecommendationDao.alreadySaved(userId, recommendationId)) {
                savedRecommendationDao.updateSavedAt(userId, recommendationId);
                return SaveResult.ok("Recommendation already saved; timestamp updated.");
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