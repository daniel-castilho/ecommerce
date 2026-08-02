package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.User;

/** Input port: updates the profile fields of an existing user. */
public interface UpdateProfileUseCase {
    User updateProfile(String userId, String fullName);
}
