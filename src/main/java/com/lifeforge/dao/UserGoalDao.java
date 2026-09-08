package com.lifeforge.dao;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.UserGoal;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the user_goals table. Tracks which goal a user
 * currently has selected, deactivating any previous selection.
 */
public class UserGoalDao {

    public UserGoal selectGoal(Long userId, Long goalId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                deactivateExisting(conn, userId);

                String insertSql = "INSERT INTO user_goals (user_id, goal_id, active, selected_at) " +
                        "VALUES (?, ?, TRUE, NOW()) RETURNING id, selected_at";
                UserGoal userGoal = new UserGoal();
                userGoal.setUserId(userId);
                userGoal.setGoalId(goalId);
                userGoal.setActive(true);

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, userId);
                    ps.setLong(2, goalId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            userGoal.setId(rs.getLong("id"));
                            userGoal.setSelectedAt(rs.getTimestamp("selected_at").toLocalDateTime());
                        }
                    }
                }

                conn.commit();
                return userGoal;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void deactivateExisting(Connection conn, Long userId) throws SQLException {
        String sql = "UPDATE user_goals SET active = FALSE WHERE user_id = ? AND active = TRUE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public Optional<UserGoal> findActiveGoalForUser(Long userId) throws SQLException {
        String sql = "SELECT * FROM user_goals WHERE user_id = ? AND active = TRUE " +
                "ORDER BY selected_at DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<UserGoal> findHistoryForUser(Long userId) throws SQLException {
        String sql = "SELECT * FROM user_goals WHERE user_id = ? ORDER BY selected_at DESC";
        List<UserGoal> history = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(mapRow(rs));
                }
            }
        }
        return history;
    }

    /**
     * Goal-distribution counts across all users' currently active goal.
     * Used by AnalyticsService - key is goal_id, value is user count.
     */
    public List<Object[]> countUsersPerActiveGoal() throws SQLException {
        String sql = "SELECT goal_id, COUNT(*) AS user_count FROM user_goals " +
                "WHERE active = TRUE GROUP BY goal_id ORDER BY user_count DESC";
        List<Object[]> results = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new Object[]{rs.getLong("goal_id"), rs.getLong("user_count")});
            }
        }
        return results;
    }

    private UserGoal mapRow(ResultSet rs) throws SQLException {
        return new UserGoal(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("goal_id"),
                rs.getBoolean("active"),
                rs.getTimestamp("selected_at").toLocalDateTime()
        );
    }
}