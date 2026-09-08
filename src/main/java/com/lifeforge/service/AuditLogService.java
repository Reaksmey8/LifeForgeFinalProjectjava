package com.lifeforge.service;

import com.lifeforge.dao.AuditLogDao;
import com.lifeforge.model.AuditLog;

import java.sql.SQLException;
import java.util.List;

/**
 * Records important administrative (and select user) actions.
 * Called by AdminService, AuthService, and other services whenever
 * a notable action occurs.
 */
public class AuditLogService {

    private final AuditLogDao auditLogDao;

    public AuditLogService(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public void log(Long actorUserId, String action, String targetType, Long targetId, String details) {
        try {
            AuditLog log = new AuditLog(null, actorUserId, action, targetType, targetId, details, null);
            auditLogDao.create(log);
        } catch (SQLException e) {
            // Audit logging must never crash the primary operation it accompanies.
            System.err.println("[LifeForge] Failed to write audit log: " + e.getMessage());
        }
    }

    public List<AuditLog> getRecentLogs(int limit) throws SQLException {
        return auditLogDao.findRecent(limit);
    }

    public List<AuditLog> getAllLogs() throws SQLException {
        return auditLogDao.findAll();
    }
}