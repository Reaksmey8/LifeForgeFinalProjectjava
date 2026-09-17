package com.lifeforge.model;

import java.time.Instant;

/**
 * In-memory temporary model holding a password reset verification code (OTP)
 * with a 5-minute time-to-live.
 */
public class PasswordResetToken {

    private final Long userId;
    private final String username;
    private final String email;
    private final String code;
    private final Instant expiryTime;

    public PasswordResetToken(String username, String code, Instant expiryTime) {
        this(null, username, null, code, expiryTime);
    }

    public PasswordResetToken(Long userId, String username, String email, String code, Instant expiryTime) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.code = code;
        this.expiryTime = expiryTime;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getCode() {
        return code;
    }

    public Instant getExpiryTime() {
        return expiryTime;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiryTime);
    }
}
