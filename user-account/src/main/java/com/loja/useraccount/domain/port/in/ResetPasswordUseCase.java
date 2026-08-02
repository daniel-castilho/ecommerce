package com.loja.useraccount.domain.port.in;

/** Input port: completes a password reset using a valid reset token. */
public interface ResetPasswordUseCase {
    void resetPassword(String token, String newPassword);
}
