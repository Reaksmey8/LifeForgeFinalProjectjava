package com.lifeforge;

import com.lifeforge.model.User;

/**
 * Holds the currently authenticated user for the lifetime of the
 * application session. Populated by AuthController on login or
 * registration and used by every screen through AppContext.
 */
public class Session {

    private User currentUser;

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void clear() {
        this.currentUser = null;
    }
}