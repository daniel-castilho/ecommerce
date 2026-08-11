package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;

/**
 * JPA implementation of {@link NotificationDeliveryLogPort}. Claiming uses a native
 * {@code INSERT ... ON CONFLICT DO NOTHING} so a concurrent or repeated event is a
 * no-op at the database level (unique {@code idempotency_key}); parameters are
 * positional (?N) because EclipseLink does not bind named parameters in native SQL.
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
                                + "idempotency_key, created_at, updated_at) "
                                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9) "
                                + "ON CONFLICT (idempotency_key) DO NOTHING")
                .setParameter(1, delivery.getId())
                .setParameter(2, delivery.getEventType())
                .setParameter(3, delivery.getAggregateId())
                .setParameter(4, delivery.getChannel().name())
                .setParameter(5, delivery.getStatus().name())
                .setParameter(6, delivery.getAttemptCount())
                .setParameter(7, delivery.getIdempotencyKey())
                .setParameter(8, delivery.getCreatedAt())
                .setParameter(9, delivery.getUpdatedAt())
                .executeUpdate();
        return inserted > 0;
    }

    @Override
    public void updateStatus(String idempotencyKey, NotificationDeliveryStatus status, String errorMessage) {
        NotificationDeliveryLogJpaEntity entity = findByKey(idempotencyKey);
        if (entity == null) {
            return;
        }
        entity.setStatus(status);
        entity.setErrorMessage(status == NotificationDeliveryStatus.FAILED ? errorMessage : null);
        entity.setAttemptCount(entity.getAttemptCount() + (status == NotificationDeliveryStatus.FAILED ? 1 : 0));
        entity.setUpdatedAt(Instant.now());
        em.merge(entity);
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