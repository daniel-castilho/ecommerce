package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.User;
import java.util.Optional;

/** Input port: retrieves a user's profile data by user ID. */
public interface GetUserProfileUseCase {
    Optional<User> getUserProfile(String userId);
}
