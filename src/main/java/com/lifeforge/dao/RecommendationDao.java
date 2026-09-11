package com.lifeforge.dao;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Recommendation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecommendationDao {

    // =========================================================
    // CREATE
    // =========================================================

    public Recommendation create(Recommendation r) throws SQLException {

        String sql = """
                INSERT INTO recommendations
                (
                    goal_id,
                    category_id,
                    activity_level,
                    title,
                    description,
                    recommended_actions,
                    suggested_target,
                    examples,
                    important_notes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, r.getGoalId());
            ps.setLong(2, r.getCategoryId());

            if (r.getActivityLevel() != null) {
                ps.setString(3, r.getActivityLevel().name());
            } else {
                ps.setString(3, "ALL");
            }

            ps.setString(4, r.getTitle());
            ps.setString(5, r.getDescription());
            ps.setString(6, r.getRecommendedActions());
            ps.setString(7, r.getSuggestedTarget());
            ps.setString(8, r.getExamples());
            ps.setString(9, r.getImportantNotes());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    r.setId(rs.getLong("id"));
                }
            }
        }

        return r;
    }


    // =========================================================
    // FIND BY ID
    // =========================================================

    public Optional<Recommendation> findById(Long id) throws SQLException {

        String sql = """
                SELECT *
                FROM recommendations
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }


    // =========================================================
    // FIND BEST MATCH
    // =========================================================

    public Optional<Recommendation> findBestMatch(
            Long goalId,
            Long categoryId,
            ActivityLevel activityLevel
    ) throws SQLException {
        // 1. Direct match on the specified category
        Optional<Recommendation> direct = findDirectMatch(goalId, categoryId, activityLevel);
        if (direct.isPresent()) {
            return direct;
        }

        // 2. Child category fallback ONLY if no direct recommendation exists
        return findChildCategoryMatch(goalId, categoryId, activityLevel);
    }

    public Optional<Recommendation> findDirectMatch(
            Long goalId,
            Long categoryId,
            ActivityLevel activityLevel
    ) throws SQLException {

        String sql = """
                SELECT *
                FROM recommendations
                WHERE goal_id = ?
                  AND category_id = ?
                  AND (
                        activity_level = ?
                        OR activity_level = 'ALL'
                        OR activity_level IS NULL
                  )
                ORDER BY
                    CASE
                        WHEN activity_level = ? THEN 1
                        WHEN activity_level = 'ALL' THEN 2
                        WHEN activity_level IS NULL THEN 3
                        ELSE 4
                    END,
                    id
                LIMIT 1
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, goalId);
            ps.setLong(2, categoryId);

            String activity =
                    activityLevel != null
                            ? activityLevel.name()
                            : "ALL";

            ps.setString(3, activity);
            ps.setString(4, activity);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Recommendation> findChildCategoryMatch(
            Long goalId,
            Long parentCategoryId,
            ActivityLevel activityLevel
    ) throws SQLException {

        String sql = """
                SELECT *
                FROM recommendations
                WHERE goal_id = ?
                  AND category_id IN (
                      SELECT id FROM recommendation_categories WHERE parent_category_id = ?
                  )
                  AND (
                        activity_level = ?
                        OR activity_level = 'ALL'
                        OR activity_level IS NULL
                  )
                ORDER BY
                    CASE
                        WHEN activity_level = ? THEN 1
                        WHEN activity_level = 'ALL' THEN 2
                        WHEN activity_level IS NULL THEN 3
                        ELSE 4
                    END,
                    id
                LIMIT 1
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, goalId);
            ps.setLong(2, parentCategoryId);

            String activity =
                    activityLevel != null
                            ? activityLevel.name()
                            : "ALL";

            ps.setString(3, activity);
            ps.setString(4, activity);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    public boolean hasRecommendationsForGoal(Long goalId) throws SQLException {
        if (goalId == null) return false;
        String sql = "SELECT 1 FROM recommendations WHERE goal_id = ? LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, goalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean hasActivitySpecificRecommendation(Long goalId, ActivityLevel activityLevel) throws SQLException {
        if (goalId == null || activityLevel == null) return false;
        String sql = "SELECT 1 FROM recommendations WHERE goal_id = ? AND activity_level = ? LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, goalId);
            ps.setString(2, activityLevel.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }


    // =========================================================
    // FIND BY GOAL + CATEGORY
    // =========================================================

    public List<Recommendation> findByGoalAndCategory(
            Long goalId,
            Long categoryId
    ) throws SQLException {

        String sql = """
                SELECT *
                FROM recommendations
                WHERE goal_id = ?
                  AND category_id = ?
                ORDER BY id
                """;

        List<Recommendation> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, goalId);
            ps.setLong(2, categoryId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }

        return results;
    }


    // =========================================================
    // FIND ALL
    // =========================================================

    public List<Recommendation> findAll() throws SQLException {

        String sql = """
                SELECT *
                FROM recommendations
                ORDER BY id DESC
                """;

        List<Recommendation> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }

        return results;
    }


    // =========================================================
    // COUNT
    // =========================================================

    public long countAll() throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM recommendations
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            rs.next();
            return rs.getLong(1);
        }
    }


    // =========================================================
    // UPDATE
    // =========================================================

    public void update(Recommendation r) throws SQLException {

        String sql = """
                UPDATE recommendations
                SET
                    goal_id = ?,
                    category_id = ?,
                    activity_level = ?,
                    title = ?,
                    description = ?,
                    recommended_actions = ?,
                    suggested_target = ?,
                    examples = ?,
                    important_notes = ?,
                    updated_at = NOW()
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, r.getGoalId());
            ps.setLong(2, r.getCategoryId());

            if (r.getActivityLevel() != null) {
                ps.setString(3, r.getActivityLevel().name());
            } else {
                ps.setString(3, "ALL");
            }

            ps.setString(4, r.getTitle());
            ps.setString(5, r.getDescription());
            ps.setString(6, r.getRecommendedActions());
            ps.setString(7, r.getSuggestedTarget());
            ps.setString(8, r.getExamples());
            ps.setString(9, r.getImportantNotes());

            ps.setLong(10, r.getId());

            ps.executeUpdate();
        }
    }


    // =========================================================
    // DELETE
    // =========================================================

    public void delete(Long id) throws SQLException {

        String sql = """
                DELETE FROM recommendations
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }


    // =========================================================
    // POPULAR CATEGORIES
    // REQUIRED BY AnalyticsService
    // =========================================================

    public List<Object[]> findPopularCategories(int limit)
            throws SQLException {

        String sql = """
                SELECT
                    rc.name AS category_name,
                    COUNT(sr.id) AS save_count
                FROM recommendations r
                JOIN recommendation_categories rc
                    ON rc.id = r.category_id
                LEFT JOIN saved_recommendations sr
                    ON sr.recommendation_id = r.id
                GROUP BY rc.id, rc.name
                ORDER BY save_count DESC
                LIMIT ?
                """;

        List<Object[]> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    results.add(new Object[]{
                            rs.getString("category_name"),
                            rs.getLong("save_count")
                    });
                }
            }
        }

        return results;
    }


    // =========================================================
    // MAP DATABASE ROW
    // =========================================================

    private Recommendation mapRow(ResultSet rs)
            throws SQLException {

        Recommendation r = new Recommendation();

        r.setId(rs.getLong("id"));

        r.setGoalId(
                rs.getLong("goal_id")
        );

        r.setCategoryId(
                rs.getLong("category_id")
        );

        String activityLevelStr =
                rs.getString("activity_level");

        // "ALL" means "applies to every activity level" and must be
        // treated the same as NULL - it is NOT a real ActivityLevel
        // enum constant and must never be passed to fromString().
        if (activityLevelStr != null &&
                !activityLevelStr.isBlank() &&
                !activityLevelStr.equalsIgnoreCase("ALL")) {

            r.setActivityLevel(
                    ActivityLevel.fromString(activityLevelStr)
            );
        }

        r.setTitle(
                rs.getString("title")
        );

        r.setDescription(
                rs.getString("description")
        );

        r.setRecommendedActions(
                rs.getString("recommended_actions")
        );

        r.setSuggestedTarget(
                rs.getString("suggested_target")
        );

        r.setExamples(
                rs.getString("examples")
        );

        r.setImportantNotes(
                rs.getString("important_notes")
        );

        return r;
    }
}