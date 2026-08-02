package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.User;
import java.util.Optional;

/**
 * Input port: validates raw credentials against the stored Argon2id hash without
 * creating a session. Used by the Jakarta Security {@code IdentityStore} so the
 * container caller (and its roles) is established from the same domain rules that
 * gate the login use case.
 */
public interface ValidateCredentialsUseCase {
    /**
     * Verifies credentials and records the outcome (failed-attempt counter / lockout).
     *
     * @return the user when credentials are valid and the account may log in; empty
     *         otherwise (wrong password, unknown email, locked or inactive account)
     */
    Optional<User> validateCredentials(String email, String plainPassword);
}
