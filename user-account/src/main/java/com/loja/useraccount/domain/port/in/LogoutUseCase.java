package com.loja.useraccount.domain.port.in;

/** Input port: terminates the current user session. */
public interface LogoutUseCase {
    void logout(String userId);
}
