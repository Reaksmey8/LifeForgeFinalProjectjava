package com.lifeforge.service;

/**
 * Result of a "Why This Recommendation?" explanation request.
 * Carries whether the text actually came from the AI service or
 * from the rule-based fallback, so the TUI can tell the user which
 * one they're looking at (spec requirement: never hide that AI
 * failed and a fallback is being shown).
 */
public final class ExplanationOutcome {

    public final String text;
    public final boolean fromAi;

    public ExplanationOutcome(String text, boolean fromAi) {
        this.text = text;
        this.fromAi = fromAi;
    }
}