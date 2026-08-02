package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.User;
import java.util.Optional;

/** Input port: returns the currently logged-in user from the session. */
public interface GetCurrentUserUseCase {
    Optional<User> getCurrentUser();
}
