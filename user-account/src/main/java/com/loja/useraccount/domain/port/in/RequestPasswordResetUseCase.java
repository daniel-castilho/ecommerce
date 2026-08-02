package com.loja.useraccount.domain.port.in;

/** Input port: initiates a password reset flow by sending a reset token to the user's email. */
public interface RequestPasswordResetUseCase {
    void requestPasswordReset(String email);
}
