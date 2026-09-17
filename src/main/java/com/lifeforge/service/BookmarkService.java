package com.lifeforge.service;

import com.lifeforge.dao.SavedRecommendationDao;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.SavedRecommendation;

import java.sql.SQLException;
import java.util.List;

/**
 * Service for bookmarking / saving recommendations.
 * Ensures duplicate saves update the timestamp or prevent redundant additions
 * rather than appending duplicate entries.
 */
public class BookmarkService {

    private final SavedRecommendationDao savedRecommendationDao;
    private final SavedRecommendationService savedRecommendationService;

    public BookmarkService(SavedRecommendationDao savedRecommendationDao,
                           SavedRecommendationService savedRecommendationService) {
        this.savedRecommendationDao = savedRecommendationDao;
        this.savedRecommendationService = savedRecommendationService;
    }

    public BookmarkService(SavedRecommendationDao savedRecommendationDao) {
        this(savedRecommendationDao, new SavedRecommendationService(savedRecommendationDao));
    }

    /**
     * Bookmarks a recommendation for a user.
     * Uses the recommendation ID check to prevent duplicates.
     */
    public SavedRecommendationService.SaveResult bookmark(Long userId, Recommendation rec) {
        if (rec == null || rec.getId() == null) {
            return SavedRecommendationService.SaveResult.fail("Invalid recommendation.");
        }
        return bookmark(userId, rec.getId());
    }

    /**
     * Bookmarks a recommendation by ID for a user.
     * If already exists, updates the savedAt timestamp.
     */
    public SavedRecommendationService.SaveResult bookmark(Long userId, Long recommendationId) {
        if (userId == null || recommendationId == null) {
            return SavedRecommendationService.SaveResult.fail("User ID and Recommendation ID are required.");
        }
        return savedRecommendationService.saveRecommendation(userId, recommendationId);
    }

    public boolean isBookmarked(Long userId, Long recommendationId) {
        return savedRecommendationService.isAlreadySaved(userId, recommendationId);
    }

    public List<SavedRecommendation> listBookmarks(Long userId) throws SQLException {
        return savedRecommendationService.listSavedForUser(userId);
    }

    public SavedRecommendationService.SaveResult removeBookmark(Long savedRecommendationId, Long userId) {
        return savedRecommendationService.deleteSaved(savedRecommendationId, userId);
    }
}
