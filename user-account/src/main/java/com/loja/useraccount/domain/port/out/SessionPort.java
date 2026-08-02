package com.loja.useraccount.domain.port.out;

import com.loja.useraccount.domain.model.User;
import java.util.Optional;

/** Output port for HTTP session management. */
public interface SessionPort {
    /** Creates a session for the given authenticated user (30-minute timeout). */
    void createSession(User user);

    /** Returns the currently logged-in user, or empty if no session exists. */
    Optional<User> getCurrentUser();

    /** Invalidates the current session (logout). */
    void invalidateSession();
}
