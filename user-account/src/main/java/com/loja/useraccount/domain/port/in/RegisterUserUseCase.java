package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.User;

/** Input port: registers a new user with email, password and name. */
public interface RegisterUserUseCase {
    /**
     * Registers a new user.
     * Throws {@link com.loja.useraccount.domain.exception.EmailAlreadyRegisteredException}
     * if the email is already taken.
     */
    User register(String email, String password, String fullName);
}
