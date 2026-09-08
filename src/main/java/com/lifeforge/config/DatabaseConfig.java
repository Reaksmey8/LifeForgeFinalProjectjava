package com.lifeforge.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Centralized PostgreSQL connection configuration.
 *
 * Environment variables (either naming style is accepted):
 *   DB_URL / LIFEFORGE_DB_URL
 *   DB_USERNAME / LIFEFORGE_DB_USER
 *   DB_PASSWORD / LIFEFORGE_DB_PASSWORD
 */
public final class DatabaseConfig {

    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/lifeforge_db";
    private static final String DEFAULT_USERNAME = "postgres";
    private static final String DEFAULT_PASSWORD = "YourNewPassword123";

    private DatabaseConfig() {
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String getUrl() {
        String url = firstNonBlank(System.getenv("DB_URL"), System.getenv("LIFEFORGE_DB_URL"));
        return url != null ? url : DEFAULT_URL;
    }

    private static String getUsername() {
        String user = firstNonBlank(System.getenv("DB_USERNAME"), System.getenv("LIFEFORGE_DB_USER"));
        return user != null ? user : DEFAULT_USERNAME;
    }

    private static String getPassword() {
        String pass = firstNonBlank(System.getenv("DB_PASSWORD"), System.getenv("LIFEFORGE_DB_PASSWORD"));
        return pass != null ? pass : DEFAULT_PASSWORD;
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver not found on classpath.", e);
        }
        Properties props = new Properties();
        props.setProperty("user", getUsername());
        props.setProperty("password", getPassword());
        // Bound how long we wait for PostgreSQL. Without these, an unreachable host
        // (DB not running, wrong host, firewall) can hang the JVM's connect() call for
        // a long time -- which looks exactly like the whole TUI has frozen at startup,
        // since Main.java checks the connection before the first screen is painted.
        props.setProperty("connectTimeout", "5");
        props.setProperty("socketTimeout", "10");
        props.setProperty("loginTimeout", "5");
        DriverManager.setLoginTimeout(5);
        return DriverManager.getConnection(getUrl(), props);
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Human-readable status of the database connection, safe to show to the user.
     * The password is never included; only the host/database (from the JDBC URL)
     * and a short, anonymized reason for a failed connection are reported.
     *
     * @return {@code null} when connected, otherwise a message describing the problem.
     */
    public static String connectionStatus() {
        String url = getUrl();
        try (Connection conn = getConnection()) {
            if (conn != null && !conn.isClosed()) {
                return null;
            }
            return "Database connection is closed.";
        } catch (SQLException e) {
            String reason = e.getMessage();
            if (reason != null) {
                // Keep it short and omit anything that looks like credentials.
                int nl = reason.indexOf('\n');
                if (nl >= 0) {
                    reason = reason.substring(0, nl);
                }
            }
            return "Could not connect to PostgreSQL at " + safeHost(url)
                    + (reason == null || reason.isBlank() ? "" : " (" + reason.trim() + ")");
        }
    }

    /**
     * Extracts only the host/database portion of a JDBC URL for display. Never prints
     * credentials; a {@code jdbc:postgresql://} URL cannot contain a password anyway,
     * but this is kept defensive.
     */
    private static String safeHost(String url) {
        if (url == null) {
            return "localhost:5432";
        }
        int idx = url.indexOf("//");
        if (idx >= 0) {
            String rest = url.substring(idx + 2);
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                rest = rest.substring(0, slash);
            }
            return rest;
        }
        return url;
    }
}