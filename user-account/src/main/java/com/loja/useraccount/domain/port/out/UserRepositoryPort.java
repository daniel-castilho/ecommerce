package com.loja.useraccount.domain.port.out;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.application.dto.UserSearchCriteria;
import com.loja.useraccount.domain.model.User;
import java.time.Instant;
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
}
