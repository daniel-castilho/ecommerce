package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.Role;

/** Input port: checks whether the current session user holds a given role. */
public interface CheckUserRoleUseCase {
    boolean currentUserHasRole(Role role);
}
