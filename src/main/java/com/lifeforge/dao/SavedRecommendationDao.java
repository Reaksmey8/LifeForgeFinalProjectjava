package com.lifeforge.dao;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.SavedRecommendation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for saved_recommendations. A UNIQUE constraint on
 * (user_id, recommendation_id) at the database level prevents
 * accidental duplicate saves; this DAO also checks proactively
 * so callers can show a friendly "already saved" message.
 */
public class SavedRecommendationDao {

    public Optional<SavedRecommendation> findSavedBySameCategory(Long userId, Long recommendationId) throws SQLException {
        String sql = """
                SELECT sr.id, sr.user_id, sr.recommendation_id, sr.saved_at
                FROM saved_recommendations sr
                JOIN recommendations r ON r.id = sr.recommendation_id
                JOIN recommendation_categories rc ON rc.id = r.category_id
                WHERE sr.user_id = ?
                  AND COALESCE(rc.parent_category_id, rc.id) = (
                      SELECT COALESCE(rc2.parent_category_id, rc2.id)
                      FROM recommendations r2
                      JOIN recommendation_categories rc2 ON rc2.id = r2.category_id
                      WHERE r2.id = ?
                  )
                ORDER BY sr.saved_at DESC
                LIMIT 1
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, recommendationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public void updateSavedRecommendation(Long savedRecommendationId, Long newRecommendationId) throws SQLException {
        String sql = "UPDATE saved_recommendations SET recommendation_id = ?, saved_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, newRecommendationId);
            ps.setLong(2, savedRecommendationId);
            ps.executeUpdate();
        }
    }

    public boolean alreadySaved(Long userId, Long recommendationId) throws SQLException {
        String sql = "SELECT 1 FROM saved_recommendations WHERE user_id = ? AND recommendation_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, recommendationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void updateSavedAt(Long userId, Long recommendationId) throws SQLException {
        String sql = "UPDATE saved_recommendations SET saved_at = NOW() WHERE user_id = ? AND recommendation_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, recommendationId);
            ps.executeUpdate();
        }
    }

    public SavedRecommendation save(Long userId, Long recommendationId) throws SQLException {
        String sql = "INSERT INTO saved_recommendations (user_id, recommendation_id, saved_at) " +
                "VALUES (?, ?, NOW()) RETURNING id, saved_at";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, recommendationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SavedRecommendation(
                            rs.getLong("id"), userId, recommendationId,
                            rs.getTimestamp("saved_at").toLocalDateTime());
                }
            }
        }
        throw new SQLException("Failed to save recommendation.");
    }

    public List<SavedRecommendation> findByUser(Long userId) throws SQLException {
        String sql = "SELECT * FROM saved_recommendations WHERE user_id = ? ORDER BY saved_at DESC";
        List<SavedRecommendation> results = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    public long countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM saved_recommendations";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public void delete(Long savedRecommendationId, Long userId) throws SQLException {
        // userId included in WHERE clause so a user can only delete their own saved items
        String sql = "DELETE FROM saved_recommendations WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, savedRecommendationId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    private SavedRecommendation mapRow(ResultSet rs) throws SQLException {
        return new SavedRecommendation(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("recommendation_id"),
                rs.getTimestamp("saved_at").toLocalDateTime()
        );
    }

    public List<Object[]> findPopularCategories(int limit) throws SQLException {
        String sql =
                "SELECT rc.name, COUNT(sr.id) AS save_count " +
                        "FROM recommendations r " +
                        "JOIN recommendation_categories rc ON rc.id = r.category_id " +
                        "LEFT JOIN saved_recommendations sr ON sr.recommendation_id = r.id " +
                        "GROUP BY rc.name " +
                        "ORDER BY save_count DESC " +
                        "LIMIT ?";

        List<Object[]> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Object[]{
                            rs.getString("name"),
                            rs.getLong("save_count")
                    });
                }
            }
        }

        return results;
    }
}