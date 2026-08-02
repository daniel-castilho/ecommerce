package com.loja.useraccount.domain.port.in;

/**
 * Input port: changes a user's password after validating the current one.
 *
 * @throws com.loja.useraccount.domain.exception.InvalidPasswordException
 *         if the current password is incorrect
 */
public interface ChangePasswordUseCase {
    void changePassword(String userId, String currentPassword, String newPassword);
}
