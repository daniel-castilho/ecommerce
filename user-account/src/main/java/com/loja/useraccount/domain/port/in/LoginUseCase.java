package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.User;

/**
 * Input port: authenticates a user by email and password.
 * Creates a session on success. Increments failed-login counter and locks
 * the account after 5 consecutive failures.
 */
public interface LoginUseCase {
    /**
     * @throws com.loja.useraccount.domain.exception.InvalidPasswordException
     *         if credentials are wrong or the account is locked/inactive
     */
    User login(String email, String password);

    /**
     * Opens the application session for an already-authenticated user and records
     * the login event. Used after the container has validated the caller via the
     * security identity store.
     */
    void establishSession(String email);
}
