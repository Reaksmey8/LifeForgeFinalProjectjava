package com.lifeforge.model;

import java.time.LocalDateTime;

/**
 * Represents a single password-reset verification code.
 *
 * <p>The {@code codeHash} field always holds a hash of the 6-digit verification
 * code (produced with the same BCrypt helper used for passwords); the plaintext
 * code is never persisted. Each record tracks an attempt budget and single-use
 * state so codes expire, cannot be replayed, and cannot be brute-forced
 * indefinitely.
 */
public class PasswordReset {

    private Long id;
    private Long userId;
    private String codeHash;
    private LocalDateTime expiresAt;
    private int attemptCount;
    private boolean used;
    private String status;
    private LocalDateTime createdAt;

    public PasswordReset() {
    }

    public PasswordReset(Long id, Long userId, String codeHash, LocalDateTime expiresAt,
                         int attemptCount, boolean used, String status, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attemptCount = attemptCount;
        this.used = used;
        this.status = status;
        this.createdAt = createdAt;
    }

    /**
     * The administrative state machine value: PENDING / APPROVED / REJECTED / COMPLETED.
     * Defaults to PENDING when the row predates the status column.
     */
    public String getStatus() {
        return status == null || status.isBlank() ? "PENDING" : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}