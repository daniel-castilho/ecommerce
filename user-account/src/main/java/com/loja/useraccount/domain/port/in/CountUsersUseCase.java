package com.loja.useraccount.domain.port.in;

/** Input port: aggregate user counts (consumed by the admin-dashboard module). */
public interface CountUsersUseCase {

    /** Total number of registered users. */
    long countAll();
}
