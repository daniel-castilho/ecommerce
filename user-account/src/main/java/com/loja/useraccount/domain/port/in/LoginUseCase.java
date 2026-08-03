package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.User;

/**
 * Input port: authenticates a user by email and password.
 * Creates a session on success. Increments failed-login counter and locks
 * the account after 5 consecutive failures.
 *
 * <p><b>Not used by the JSF web layer.</b> Since real Jakarta Security RBAC was wired up
 * (see {@code UserIdentityStore} / {@code LoginAuthenticationMechanism}), the container
 * is the single point of credential verification for browser logins: {@code LoginBean}
 * calls {@code HttpServletRequest.login(...)}, which re-enters the container and
 * delegates to {@link ValidateCredentialsUseCase} — one Argon2id comparison, one save.
 * {@code LoginBean} then calls {@link #establishSession(String)} (no password check) to
 * open the application session.
 *
 * <p>Calling {@link #login(String, String)} <i>in addition to</i>
 * {@code HttpServletRequest.login(...)} for the same attempt re-runs the identical domain
 * check independently — a second Argon2id comparison (deliberately expensive) and a
 * second persist, for every successful login. This happened once; see
 * {@code docs/lessons.md} and the {@code LoginBeanTest} regression test before wiring a
 * new caller.
 *
 * <p>This method remains the correct entry point for callers with no servlet container
 * request to delegate to — a future REST API, a CLI/batch job, or a test that exercises
 * the domain directly.
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
