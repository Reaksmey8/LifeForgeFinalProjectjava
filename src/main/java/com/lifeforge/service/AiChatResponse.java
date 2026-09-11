package com.lifeforge.service;

/**
 * A transient response for the optional AI assistant. Chat is deliberately
 * not persisted and never represents a new LifeForge recommendation.
 */
public final class AiChatResponse {

    public final String text;
    public final boolean fromAi;
    public final Long suggestedCategoryId;
    public final String suggestedCategoryName;

    public AiChatResponse(String text, boolean fromAi) {
        this(text, fromAi, null, null);
    }

    public AiChatResponse(String text, boolean fromAi, Long suggestedCategoryId, String suggestedCategoryName) {
        this.text = text;
        this.fromAi = fromAi;
        this.suggestedCategoryId = suggestedCategoryId;
        this.suggestedCategoryName = suggestedCategoryName;
    }
}
