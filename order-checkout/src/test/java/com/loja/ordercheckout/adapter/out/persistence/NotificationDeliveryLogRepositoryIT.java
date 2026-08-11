package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.NotificationChannel;
import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NotificationDeliveryLogRepositoryIT extends AbstractIntegrationTest {

    private NotificationDeliveryLogRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new NotificationDeliveryLogRepositoryAdapter();
        adapter.em = em;
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE tb_notification_delivery_log RESTART IDENTITY CASCADE")
                .executeUpdate();
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    private <T> T inTx(Supplier<T> operation) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            T result = operation.get();
            tx.commit();
            return result;
        } catch (RuntimeException | Error e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.clear();
        }
    }

    @Test
    void claim_newKey_returnsTrueAndPersistsPendingRow() {
        NotificationDelivery delivery = NotificationDelivery.create(
                "ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1", NotificationChannel.EMAIL);

        boolean claimed = inTx(() -> adapter.claim(delivery));

        assertThat(claimed).isTrue();
        NotificationDelivery stored = inTx(() -> find("ORDER_CONFIRMED:o-1"));
        assertThat(stored).isNotNull();
        assertThat(stored.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        assertThat(stored.getEventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(stored.getAggregateId()).isEqualTo("o-1");
        assertThat(stored.getChannel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    void claim_sameKeyAgain_returnsFalseAndKeepsSingleRow() {
        NotificationDelivery first = NotificationDelivery.create(
                "ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1", NotificationChannel.EMAIL);
        NotificationDelivery second = NotificationDelivery.create(
                "ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1", NotificationChannel.EMAIL);
        inTx(() -> adapter.claim(first));

        boolean reClaimed = inTx(() -> adapter.claim(second));

        assertThat(reClaimed).isFalse();
        Long count = inTx(() -> em.createQuery(
                        "SELECT COUNT(e) FROM NotificationDeliveryLogJpaEntity e", Long.class)
                .getSingleResult());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void updateStatus_failed_recordsErrorAndBumpsAttemptCount() {
        NotificationDelivery delivery = NotificationDelivery.create(
                "ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1", NotificationChannel.EMAIL);
        inTx(() -> adapter.claim(delivery));

        inTx(() -> {
            adapter.updateStatus("ORDER_CONFIRMED:o-1", NotificationDeliveryStatus.FAILED,
                    "Connection refused");
            return null;
        });

        NotificationDelivery stored = inTx(() -> find("ORDER_CONFIRMED:o-1"));
        assertThat(stored.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(stored.getAttemptCount()).isEqualTo(2);
        assertThat(stored.getErrorMessage()).isEqualTo("Connection refused");
    }

    @Test
    void updateStatus_sent_marksSentWithoutBumpingAttempt() {
        NotificationDelivery delivery = NotificationDelivery.create(
                "ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1", NotificationChannel.EMAIL);
        inTx(() -> adapter.claim(delivery));

        inTx(() -> {
            adapter.updateStatus("ORDER_CONFIRMED:o-1", NotificationDeliveryStatus.SENT, null);
            return null;
        });

        NotificationDelivery stored = inTx(() -> find("ORDER_CONFIRMED:o-1"));
        assertThat(stored.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        assertThat(stored.getErrorMessage()).isNull();
    }

    @Test
    void updateStatus_unknownKey_isNoOp() {
        assertThatCode(() -> inTx(() -> {
            adapter.updateStatus("ORDER_CONFIRMED:ghost", NotificationDeliveryStatus.FAILED, "boom");
            return null;
        })).doesNotThrowAnyException();
    }

    private NotificationDelivery find(String idempotencyKey) {
        return em.createQuery(
                        "SELECT e FROM NotificationDeliveryLogJpaEntity e WHERE e.idempotencyKey = :key",
                        NotificationDeliveryLogJpaEntity.class)
                .setParameter("key", idempotencyKey)
                .getResultStream()
                .findFirst()
                .map(NotificationDeliveryLogJpaEntity::toDomain)
                .orElse(null);
    }
}