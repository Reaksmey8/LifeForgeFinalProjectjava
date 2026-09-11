package com.lifeforge.model;

/**
 * Deterministic recommendation priority levels for LIFEForge.
 * Priorities are calculated based on Goal, Activity Level, and Category.
 */
public enum RecommendationPriority {
    HIGH("HIGH PRIORITY", "\uD83D\uDD34", 1),         // 🔴
    RECOMMENDED("RECOMMENDED", "\uD83D\uDFE1", 2),   // 🟡
    SUPPORTING("SUPPORTING", "\uD83D\uDD35", 3);     // 🔵

    private final String label;
    private final String symbol;
    private final int rank;

    RecommendationPriority(String label, String symbol, int rank) {
        this.label = label;
        this.symbol = symbol;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getRank() {
        return rank;
    }

    public String getBadge() {
        return symbol + " " + label;
    }
}
