package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

/**
 * JPA implementation of {@link NotificationDeliveryLogPort}. Claiming uses a native
 * {@code INSERT ... ON CONFLICT DO NOTHING} so a concurrent or repeated event is a
 * no-op at the database level (unique {@code idempotency_key}); parameters are
 * positional (?N) because EclipseLink does not bind named parameters in native SQL.
 *
 * <p>Dispatch policy lives on the domain model: {@code updateStatus} bumps the attempt
 * count on FAILED and — when it reaches {@link NotificationDelivery#MAX_ATTEMPTS} —
 * escalates to EXHAUSTED instead, gating each earlier retry behind the backoff deadline
 * in {@code next_attempt_at}.
 */
@ApplicationScoped
@Transactional
public class NotificationDeliveryLogRepositoryAdapter implements NotificationDeliveryLogPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public boolean claim(NotificationDelivery delivery) {
        int inserted = em.createNativeQuery(
                        "INSERT INTO tb_notification_delivery_log "
                                + "(id, event_type, aggregate_id, channel, status, attempt_count, "
                                + "idempotency_key, recipient_email, subject, body, body_html, "
                                + "next_attempt_at, created_at, updated_at) "
                                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14) "
                                + "ON CONFLICT (idempotency_key) DO NOTHING")
                .setParameter(1, delivery.getId())
                .setParameter(2, delivery.getEventType())
                .setParameter(3, delivery.getAggregateId())
                .setParameter(4, delivery.getChannel().name())
                .setParameter(5, delivery.getStatus().name())
                .setParameter(6, delivery.getAttemptCount())
                .setParameter(7, delivery.getIdempotencyKey())
                .setParameter(8, delivery.getRecipientEmail())
                .setParameter(9, delivery.getSubject())
                .setParameter(10, delivery.getBody())
                .setParameter(11, delivery.getBodyHtml())
                .setParameter(12, delivery.getNextAttemptAt())
                .setParameter(13, delivery.getCreatedAt())
                .setParameter(14, delivery.getUpdatedAt())
                .executeUpdate();
        return inserted > 0;
    }

    @Override
    public void updateStatus(String idempotencyKey, NotificationDeliveryStatus status, String errorMessage) {
        NotificationDeliveryLogJpaEntity entity = findByKey(idempotencyKey);
        if (entity == null) {
            return;
        }
        if (status == NotificationDeliveryStatus.SENT) {
            entity.setStatus(NotificationDeliveryStatus.SENT);
            entity.setErrorMessage(null);
            entity.setNextAttemptAt(null);
        } else if (status == NotificationDeliveryStatus.FAILED) {
            int nextAttempt = entity.getAttemptCount() + 1;
            entity.setAttemptCount(nextAttempt);
            entity.setErrorMessage(errorMessage);
            if (nextAttempt >= NotificationDelivery.MAX_ATTEMPTS) {
                entity.setStatus(NotificationDeliveryStatus.EXHAUSTED);
                entity.setNextAttemptAt(null);
            } else {
                entity.setStatus(NotificationDeliveryStatus.FAILED);
                entity.setNextAttemptAt(Instant.now().plus(NotificationDelivery.backoffDelayFor(nextAttempt)));
            }
        }
        entity.setUpdatedAt(Instant.now());
        em.merge(entity);
    }

    @Override
    public List<NotificationDelivery> findDue(int limit) {
        return em.createQuery(
                        "SELECT e FROM NotificationDeliveryLogJpaEntity e "
                                + "WHERE (e.status = :pending "
                                + "OR (e.status = :failed AND e.attemptCount < :maxAttempts)) "
                                + "AND e.body IS NOT NULL "
                                + "AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now) "
                                + "ORDER BY e.nextAttemptAt ASC, e.createdAt ASC",
                        NotificationDeliveryLogJpaEntity.class)
                .setParameter("pending", NotificationDeliveryStatus.PENDING)
                .setParameter("failed", NotificationDeliveryStatus.FAILED)
                .setParameter("maxAttempts", NotificationDelivery.MAX_ATTEMPTS)
                .setParameter("now", Instant.now())
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(NotificationDeliveryLogJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<NotificationDelivery> findDeliveries(NotificationDeliveryStatus status) {
        if (status == null) {
            return em.createQuery(
                            "SELECT e FROM NotificationDeliveryLogJpaEntity e "
                                    + "ORDER BY e.createdAt DESC",
                            NotificationDeliveryLogJpaEntity.class)
                    .setMaxResults(200)
                    .getResultList()
                    .stream()
                    .map(NotificationDeliveryLogJpaEntity::toDomain)
                    .toList();
        }
        return em.createQuery(
                        "SELECT e FROM NotificationDeliveryLogJpaEntity e WHERE e.status = :status "
                                + "ORDER BY e.createdAt DESC",
                        NotificationDeliveryLogJpaEntity.class)
                .setParameter("status", status)
                .setMaxResults(200)
                .getResultList()
                .stream()
                .map(NotificationDeliveryLogJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean resend(String idempotencyKey) {
        NotificationDeliveryLogJpaEntity entity = findByKey(idempotencyKey);
        if (entity == null) {
            return false;
        }
        entity.setStatus(NotificationDeliveryStatus.PENDING);
        entity.setAttemptCount(0);
        entity.setErrorMessage(null);
        entity.setNextAttemptAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        em.merge(entity);
        return true;
    }

    private NotificationDeliveryLogJpaEntity findByKey(String idempotencyKey) {
        return em.createQuery(
                        "SELECT e FROM NotificationDeliveryLogJpaEntity e WHERE e.idempotencyKey = :key",
                        NotificationDeliveryLogJpaEntity.class)
                .setParameter("key", idempotencyKey)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}