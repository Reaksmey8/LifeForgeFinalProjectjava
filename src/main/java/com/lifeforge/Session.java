package com.lifeforge;

import com.lifeforge.model.Goal;
import com.lifeforge.model.User;

/**
 * Holds the currently authenticated user for the lifetime of the
 * application session. Populated by AuthController on login or
 * registration and used by every screen through AppContext.
 */
public class Session {

    private User currentUser;
    private Goal customGoal;

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public Goal getCustomGoal() {
        return customGoal;
    }

    public void setCustomGoal(Goal customGoal) {
        this.customGoal = customGoal;
    }

    public void clearCustomGoal() {
        this.customGoal = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void clear() {
        this.currentUser = null;
        this.customGoal = null;
    }
}