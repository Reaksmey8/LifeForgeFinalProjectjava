package com.lifeforge.controller;

/**
 * Shared behaviour for every controller: a last-error slot that
 * TUI screens read from to show a friendly message after a failed
 * operation. Controllers never leak SQL exceptions to the TUI;
 * they translate them into this message instead.
 */
public abstract class BaseController {

    private String lastError;

    protected void setError(String message) {
        this.lastError = message;
    }

    protected void setError(Exception e, String message) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        this.lastError = message + " (" + detail + ")";
    }

    public String getLastError() {
        return lastError;
    }
}