package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.Role;

/**
 * Input port (admin-only): assigns a role to a user.
 *
 * @throws com.loja.useraccount.domain.exception.InsufficientPermissionException
 *         if the caller is not an ADMIN
 */
public interface AssignRoleUseCase {
    void assignRole(String userId, Role role);
}
