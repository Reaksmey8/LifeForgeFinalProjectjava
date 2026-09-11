package com.lifeforge.dao;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.PasswordReset;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * Data access for the password_resets table. Stores only the hashed
 * verification code; the plaintext code never touches the database.
 */
public class PasswordResetDao {

    public PasswordReset create(PasswordReset reset) throws SQLException {
        String sql = "INSERT INTO password_resets (user_id, code_hash, expires_at, attempt_count, used, status) " +
                "VALUES (?, ?, ?, 0, FALSE, 'PENDING') RETURNING id, created_at";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reset.getUserId());
            ps.setString(2, reset.getCodeHash());
            ps.setTimestamp(3, Timestamp.valueOf(reset.getExpiresAt()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    reset.setId(rs.getLong("id"));
                    reset.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
            }
        }
        reset.setStatus("PENDING");
        return reset;
    }

    /** The latest still-valid (not used, not expired) code for a user, if any. */
    public Optional<PasswordReset> findActiveByUser(Long userId) throws SQLException {
        String sql = "SELECT * FROM password_resets WHERE user_id = ? AND used = FALSE " +
                "AND expires_at > NOW() ORDER BY created_at DESC LIMIT 1";
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

    /**
     * Invalidates any still-pending code for a user so a fresh code
     * supersedes it. Scoped to PENDING only - COMPLETED/REJECTED/APPROVED
     * rows are historical records and must never be deleted here.
     */
    public void invalidateForUser(Long userId) throws SQLException {
        String sql = "DELETE FROM password_resets WHERE user_id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public void markUsed(Long id) throws SQLException {
        String sql = "UPDATE password_resets SET used = TRUE WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /** Marks the request completed (used + COMPLETED) when the new password is set. */
    public void markCompleted(Long id) throws SQLException {
        String sql = "UPDATE password_resets SET used = TRUE, status = 'COMPLETED' WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /** Transitions an admin decision (APPROVED / REJECTED) on a request. */
    public void updateStatus(Long id, String status) throws SQLException {
        String sql = "UPDATE password_resets SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public Optional<PasswordReset> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM password_resets WHERE id = ?";
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

    /** The most recent request for a user regardless of state, if any. */
    public Optional<PasswordReset> findLatestByUser(Long userId) throws SQLException {
        String sql = "SELECT * FROM password_resets WHERE user_id = ? " +
                "ORDER BY created_at DESC, id DESC LIMIT 1";
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

    public void setAttemptCount(Long id, int attemptCount) throws SQLException {
        String sql = "UPDATE password_resets SET attempt_count = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attemptCount);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Every password-reset request, newest first (admin review listing). */
    public java.util.List<PasswordReset> findAll() throws SQLException {
        String sql = "SELECT * FROM password_resets ORDER BY created_at DESC";
        java.util.List<PasswordReset> results = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private PasswordReset mapRow(ResultSet rs) throws SQLException {
        PasswordReset reset = new PasswordReset();
        reset.setId(rs.getLong("id"));
        reset.setUserId(rs.getLong("user_id"));
        reset.setCodeHash(rs.getString("code_hash"));
        reset.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        reset.setAttemptCount(rs.getInt("attempt_count"));
        reset.setUsed(rs.getBoolean("used"));
        reset.setStatus(rs.getString("status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        reset.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return reset;
    }
}