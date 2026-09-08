package com.lifeforge.controller;

import com.lifeforge.Session;
import com.lifeforge.model.SavedRecommendation;
import com.lifeforge.model.User;
import com.lifeforge.service.SavedRecommendationService;

import java.sql.SQLException;
import java.util.List;

/**
 * Saving, listing and removing a user's saved recommendations.
 * Always scoped to the session user - a saved item can only be
 * deleted by the user who owns it.
 */
public class SavedRecommendationController extends BaseController {

    private final SavedRecommendationService savedRecommendationService;
    private final Session session;

    public SavedRecommendationController(SavedRecommendationService savedRecommendationService, Session session) {
        this.savedRecommendationService = savedRecommendationService;
        this.session = session;
    }

    public boolean save(Long recommendationId) {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return false;
        }
        SavedRecommendationService.SaveResult result =
                savedRecommendationService.saveRecommendation(current.getId(), recommendationId);
        if (!result.success) {
            setError(result.message);
            return false;
        }
        return true;
    }

    public List<SavedRecommendation> listSaved() {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return null;
        }
        try {
            return savedRecommendationService.listSavedForUser(current.getId());
        } catch (SQLException e) {
            setError(e, "Failed to load your saved recommendations.");
            return null;
        }
    }

    public boolean delete(Long savedRecommendationId) {
        User current = session.getCurrentUser();
        if (current == null) {
            setError("Not logged in.");
            return false;
        }
        SavedRecommendationService.SaveResult result =
                savedRecommendationService.deleteSaved(savedRecommendationId, current.getId());
        if (!result.success) {
            setError(result.message);
            return false;
        }
        return true;
    }
}