package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.User;
import java.util.Optional;

/** Input port: retrieves user profile data. */
public interface FindUserUseCase {
    Optional<User> findByEmail(String email);
    Optional<User> findById(String userId);
}
