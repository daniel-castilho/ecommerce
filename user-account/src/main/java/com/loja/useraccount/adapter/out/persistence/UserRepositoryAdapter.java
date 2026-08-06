package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.adapter.out.persistence.strategy.CriteriaStrategy;
import com.loja.useraccount.adapter.out.persistence.strategy.EmailCriteriaStrategy;
import com.loja.useraccount.adapter.out.persistence.strategy.StatusCriteriaStrategy;
import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.application.dto.UserSearchCriteria;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserGrowthPoint;
import com.loja.useraccount.domain.port.out.UserRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Output adapter: implements UserRepositoryPort using JPA.
 * To migrate to a different persistence technology, create a new adapter
 * implementing the same port — the domain stays untouched (Open/Closed Principle).
 */
@ApplicationScoped
public class UserRepositoryAdapter implements UserRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    private final List<CriteriaStrategy> criteriaStrategies = List.of(
            new EmailCriteriaStrategy(),
            new StatusCriteriaStrategy()
    );

    @Override
    public User save(User user) {
        UserJpaEntity entity = UserJpaEntity.fromDomain(user);
        em.merge(entity);
        return user;
    }

    @Override
    public Optional<User> findById(String userId) {
        return Optional.ofNullable(em.find(UserJpaEntity.class, userId))
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return em.createQuery(
                        "SELECT u FROM UserJpaEntity u WHERE u.email = :email",
                        UserJpaEntity.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByResetToken(String token) {
        return em.createQuery(
                        "SELECT u FROM UserJpaEntity u WHERE u.passwordResetToken = :token",
                        UserJpaEntity.class)
                .setParameter("token", token)
                .getResultStream()
                .findFirst()
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public PageResult<User> findAll(int page, int pageSize, UserSearchCriteria criteria) {
        String baseJpql = buildWhereClause(criteria);

        TypedQuery<UserJpaEntity> query = em.createQuery(
                "SELECT u FROM UserJpaEntity u" + baseJpql + " ORDER BY u.email", UserJpaEntity.class);
        TypedQuery<Long> countQuery = em.createQuery(
                "SELECT COUNT(u) FROM UserJpaEntity u" + baseJpql, Long.class);

        applyParameters(query, criteria);
        applyParameters(countQuery, criteria);

        int offset = page * pageSize;
        query.setFirstResult(offset).setMaxResults(pageSize);

        List<User> items = query.getResultList().stream()
                .map(UserJpaEntity::toDomain)
                .toList();
        long total = countQuery.getSingleResult();

        return new PageResult<>(items, total, page, pageSize);
    }

    @Override
    public void delete(String userId) {
        UserJpaEntity entity = em.find(UserJpaEntity.class, userId);
        if (entity != null) {
            em.remove(entity);
        }
    }

    @Override
    public long count() {
        return em.createQuery("SELECT COUNT(u) FROM UserJpaEntity u", Long.class)
                .getSingleResult();
    }

    @Override
    public long countCreatedSince(Instant since) {
        return em.createQuery(
                        "SELECT COUNT(u) FROM UserJpaEntity u WHERE u.createdAt >= :since", Long.class)
                .setParameter("since", since)
                .getSingleResult();
    }

    @Override
    public List<UserGrowthPoint> userGrowthSeries(Instant from, Instant to) {
        ZoneId zone = ZoneId.systemDefault();
        Map<LocalDate, Long> buckets = new TreeMap<>();
        em.createQuery(
                        "SELECT u.createdAt FROM UserJpaEntity u " +
                                "WHERE u.createdAt >= :from AND u.createdAt < :to",
                        Instant.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultStream()
                .forEach(createdAt -> {
                    LocalDate day = createdAt.atZone(zone).toLocalDate();
                    buckets.merge(day, 1L, Long::sum);
                });
        return buckets.entrySet().stream()
                .map(entry -> new UserGrowthPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public long countInactiveSince(Instant cutoff) {
        return em.createQuery(
                        "SELECT COUNT(u) FROM UserJpaEntity u " +
                                "WHERE (u.lastLoginAt IS NULL AND u.createdAt < :cutoff) " +
                                "OR (u.lastLoginAt IS NOT NULL AND u.lastLoginAt < :cutoff)",
                        Long.class)
                .setParameter("cutoff", cutoff)
                .getSingleResult();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildWhereClause(UserSearchCriteria criteria) {
        String conditions = criteriaStrategies.stream()
                .filter(s -> s.supports(criteria))
                .map(CriteriaStrategy::conditionFragment)
                .collect(Collectors.joining(" AND "));
        return conditions.isEmpty() ? "" : " WHERE " + conditions;
    }

    private <T> void applyParameters(TypedQuery<T> query, UserSearchCriteria criteria) {
        criteriaStrategies.stream()
                .filter(s -> s.supports(criteria))
                .forEach(s -> s.applyParameter(query, criteria));
    }
}
