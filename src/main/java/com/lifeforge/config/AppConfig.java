package com.lifeforge.config;

/**
 * Centralized application-wide configuration values.
 * Reads sensitive/environment-specific values from environment
 * variables so nothing is hard-coded into source.
 */
public final class AppConfig {

    private AppConfig() {
    }

    public static final String APP_NAME = "LifeForge";
    public static final String APP_TAGLINE = "Personalized Health & Lifestyle Recommendation System";
    public static final String APP_VERSION = "1.0.0";

    public static final int BCRYPT_ROUNDS = 12;

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MIN_AGE = 13;
    public static final int MAX_AGE = 100;
    public static final double MIN_HEIGHT_CM = 100.0;
    public static final double MAX_HEIGHT_CM = 250.0;
    public static final double MIN_WEIGHT_KG = 30.0;
    public static final double MAX_WEIGHT_KG = 300.0;

    // Password reset / verification codes
    public static final int RESET_CODE_LENGTH = 6;
    private static final int DEFAULT_RESET_CODE_TTL_MINUTES = 10;
    private static final int DEFAULT_RESET_CODE_MAX_ATTEMPTS = 5;

    /**
     * Whether the verification code may be logged to the console for
     * development/testing. Always OFF unless explicitly enabled with the
     * LIFEFORGE_DEV_CODE_LOG environment variable (or the
     * lifeforge.dev.code.log system property) set to true/1/yes.
     */
    public static boolean isDevResetCodeLoggingEnabled() {
        return isTrue(envOrProperty("LIFEFORGE_DEV_CODE_LOG", "lifeforge.dev.code.log"));
    }

    /** How many minutes a verification code stays valid (default 10). */
    public static int getResetCodeExpirationMinutes() {
        int value = parseInt(envOrProperty(
                "LIFEFORGE_RESET_CODE_TTL_MINUTES", "lifeforge.reset.code.ttl.minutes"),
                DEFAULT_RESET_CODE_TTL_MINUTES);
        return Math.max(1, value);
    }

    /** How many incorrect code attempts are allowed before a new code is required (default 5). */
    public static int getResetCodeMaxAttempts() {
        int value = parseInt(envOrProperty(
                "LIFEFORGE_RESET_CODE_MAX_ATTEMPTS", "lifeforge.reset.code.max.attempts"),
                DEFAULT_RESET_CODE_MAX_ATTEMPTS);
        return Math.max(3, Math.min(10, value));
    }

    private static String envOrProperty(String envName, String propertyName) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) {
            value = System.getProperty(propertyName);
        }
        return value;
    }

    private static boolean isTrue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.equalsIgnoreCase("true") || trimmed.equals("1")
                || trimmed.equalsIgnoreCase("yes");
    }

    private static int parseInt(String value, int def) {
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static String getAiApiKey() {
        return System.getenv("LIFEFORGE_AI_API_KEY");
    }

    /**
     * Whether AI-generated explanations should be attempted at all.
     * Ollama runs locally and needs no API key, so this is a simple
     * on/off switch (default: on). Set LIFEFORGE_AI_ENABLED=false to
     * force rule-based explanations only, e.g. on a machine without
     * Ollama installed.
     */
    public static boolean isAiEnabled() {
        String flag = System.getenv("LIFEFORGE_AI_ENABLED");
        return flag == null || flag.isBlank() || Boolean.parseBoolean(flag);
    }

    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_OLLAMA_MODEL = "llama3.2";

    public static String getOllamaBaseUrl() {
        String url = System.getenv("LIFEFORGE_OLLAMA_URL");
        return (url != null && !url.isBlank()) ? url : DEFAULT_OLLAMA_BASE_URL;
    }

    public static String getOllamaModel() {
        String model = System.getenv("LIFEFORGE_OLLAMA_MODEL");
        return (model != null && !model.isBlank()) ? model : DEFAULT_OLLAMA_MODEL;
    }
}