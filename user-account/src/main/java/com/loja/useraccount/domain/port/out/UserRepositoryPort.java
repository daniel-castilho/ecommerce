package com.loja.useraccount.domain.port.out;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.application.dto.UserSearchCriteria;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserGrowthPoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Output port (driven port): what the domain requires from the outside world. */
public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(String userId);
    Optional<User> findByEmail(String email);
    Optional<User> findByResetToken(String token);
    PageResult<User> findAll(int page, int pageSize, UserSearchCriteria criteria);
    void delete(String userId);

    /** Total number of registered users (admin metrics). */
    long count();

    /** Number of users registered at or after {@code since} (admin metrics). */
    long countCreatedSince(Instant since);

    /**
     * New accounts bucketed per local date for registrations in {@code [from, to)}
     * (admin reporting, backlog S22). Days without registrations are omitted;
     * bucketing uses the system default zone.
     */
    List<UserGrowthPoint> userGrowthSeries(Instant from, Instant to);

    /**
     * Number of accounts with no activity since {@code cutoff}: either they last
     * logged in before {@code cutoff}, or they never logged in and registered
     * before {@code cutoff} (admin reporting, backlog S22 — churn definition).
     */
    long countInactiveSince(Instant cutoff);
}
