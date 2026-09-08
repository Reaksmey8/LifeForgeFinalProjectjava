package com.lifeforge.dao;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.AuditLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for audit_logs. Written whenever an important
 * administrative action occurs.
 */
public class AuditLogDao {

    public AuditLog create(AuditLog log) throws SQLException {
        String sql = "INSERT INTO audit_logs (actor_user_id, action, target_type, target_id, details) " +
                "VALUES (?, ?, ?, ?, ?) RETURNING id, created_at";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (log.getActorUserId() != null) {
                ps.setLong(1, log.getActorUserId());
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setString(2, log.getAction());
            ps.setString(3, log.getTargetType());
            if (log.getTargetId() != null) {
                ps.setLong(4, log.getTargetId());
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            ps.setString(5, log.getDetails());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    log.setId(rs.getLong("id"));
                    log.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
            }
        }
        return log;
    }

    public List<AuditLog> findRecent(int limit) throws SQLException {
        String sql = "SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT ?";
        List<AuditLog> results = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    public List<AuditLog> findAll() throws SQLException {
        String sql = "SELECT * FROM audit_logs ORDER BY created_at DESC";
        List<AuditLog> results = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private AuditLog mapRow(ResultSet rs) throws SQLException {
        long actorId = rs.getLong("actor_user_id");
        Long actor = rs.wasNull() ? null : actorId;
        long targetId = rs.getLong("target_id");
        Long target = rs.wasNull() ? null : targetId;

        return new AuditLog(
                rs.getLong("id"),
                actor,
                rs.getString("action"),
                rs.getString("target_type"),
                target,
                rs.getString("details"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}