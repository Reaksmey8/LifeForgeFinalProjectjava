package com.lifeforge.dao;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.model.Role;
import com.lifeforge.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the users table. All SQL uses PreparedStatement
 * with parameter binding - never string concatenation.
 */
public class UserDao {

    public User create(User user) throws SQLException {
        String sql = "INSERT INTO users " +
                "(full_name, username, email, password_hash, age, gender, height_cm, weight_kg, activity_level, role, blocked) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at, updated_at";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getFullName());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPasswordHash());
            ps.setInt(5, user.getAge());
            ps.setString(6, user.getGender().name());
            ps.setDouble(7, user.getHeightCm());
            ps.setDouble(8, user.getWeightKg());
            ps.setString(9, user.getActivityLevel().name());
            ps.setString(10, user.getRole().name());
            ps.setBoolean(11, user.isBlocked());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    user.setId(rs.getLong("id"));
                    user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    user.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return user;
    }

    public Optional<User> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
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

    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Resolves an account by email OR username for login. */
    public Optional<User> findByEmailOrUsername(String login) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ? OR username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, login);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        // Legacy accounts created before the username column existed have a null
        // username. Allow them to sign in with the local part of their email too.
        if (!login.contains("@")) {
            String candidate = login.toLowerCase() + "@";
            sql = "SELECT * FROM users WHERE LOWER(email) LIKE ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, candidate + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
        }
        return Optional.empty();
    }

    public boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    public List<User> searchByNameOrEmail(String term) throws SQLException {
        String sql = "SELECT * FROM users WHERE LOWER(full_name) LIKE ? OR LOWER(email) LIKE ? " +
                "OR LOWER(username) LIKE ? " +
                "ORDER BY created_at DESC";
        List<User> users = new ArrayList<>();
        String pattern = "%" + term.toLowerCase() + "%";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        }
        return users;
    }

    public long countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public long countActiveUsers() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE blocked = FALSE AND role = 'USER'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public void updateProfile(User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, age = ?, gender = ?, height_cm = ?, " +
                "weight_kg = ?, activity_level = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setInt(2, user.getAge());
            ps.setString(3, user.getGender().name());
            ps.setDouble(4, user.getHeightCm());
            ps.setDouble(5, user.getWeightKg());
            ps.setString(6, user.getActivityLevel().name());
            ps.setLong(7, user.getId());
            ps.executeUpdate();
        }
    }

    public void updatePassword(Long userId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public void updateRole(Long userId, Role role) throws SQLException {
        String sql = "UPDATE users SET role = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.name());
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public void setBlocked(Long userId, boolean blocked) throws SQLException {
        String sql = "UPDATE users SET blocked = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, blocked);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public void delete(Long userId) throws SQLException {

        String deleteSavedSql =
                "DELETE FROM saved_recommendations WHERE user_id = ?";

        String deleteGoalsSql =
                "DELETE FROM user_goals WHERE user_id = ?";

        String deleteUserSql =
                "DELETE FROM users WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {

            conn.setAutoCommit(false);

            try {
                // 1. Delete saved recommendations
                try (PreparedStatement ps = conn.prepareStatement(deleteSavedSql)) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }

                // 2. Delete user's goals
                try (PreparedStatement ps = conn.prepareStatement(deleteGoalsSql)) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }

                // 3. Delete the user
                try (PreparedStatement ps = conn.prepareStatement(deleteUserSql)) {
                    ps.setLong(1, userId);

                    int rows = ps.executeUpdate();

                    if (rows == 0) {
                        throw new SQLException("User account was not found.");
                    }
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();

                System.err.println(
                        "[LifeForge] Account deletion failed: " + e.getMessage()
                );

                throw e;

            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setFullName(rs.getString("full_name"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setAge(rs.getInt("age"));
        user.setGender(Gender.fromString(rs.getString("gender")));
        user.setHeightCm(rs.getDouble("height_cm"));
        user.setWeightKg(rs.getDouble("weight_kg"));
        user.setActivityLevel(ActivityLevel.fromString(rs.getString("activity_level")));
        user.setRole(Role.fromString(rs.getString("role")));
        user.setBlocked(rs.getBoolean("blocked"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        user.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        user.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
        return user;
    }
}