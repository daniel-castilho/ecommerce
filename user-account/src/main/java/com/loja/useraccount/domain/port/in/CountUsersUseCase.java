package com.loja.useraccount.domain.port.in;

/** Input port: aggregate user counts (consumed by the admin-dashboard module). */
public interface CountUsersUseCase {

    /** Total number of registered users. */
    long countAll();

    /** Number of users registered since the start of today (server local time). */
    long countRegisteredToday();

    /** Number of users registered since the start of the current month (server local time). */
    long countRegisteredThisMonth();
}
