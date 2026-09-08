package com.lifeforge.util;

import com.lifeforge.config.AppConfig;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Handles password hashing and verification using BCrypt.
 * Passwords are never stored or compared as plain text.
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(AppConfig.BCRYPT_ROUNDS));
    }

    public static boolean verify(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            // Malformed hash in the database - fail closed.
            return false;
        }
    }
}