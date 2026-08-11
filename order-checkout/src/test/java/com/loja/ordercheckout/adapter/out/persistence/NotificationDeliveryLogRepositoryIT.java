package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.NotificationChannel;
import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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

    private NotificationDelivery draft(String idempotencyKey, String event, String aggregateId) {
        return NotificationDelivery.create(idempotencyKey, event, aggregateId, NotificationChannel.EMAIL,
                "buyer@example.com", "Order " + aggregateId + " confirmed", "Hi,\n\nYour order is confirmed.");
    }

    @Test
    void claim_newKey_returnsTrueAndPersistsPendingRowWithSnapshot() {
        NotificationDelivery delivery = draft("ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1");

        boolean claimed = inTx(() -> adapter.claim(delivery));

        assertThat(claimed).isTrue();
        NotificationDelivery stored = inTx(() -> find("ORDER_CONFIRMED:o-1"));
        assertThat(stored).isNotNull();
        assertThat(stored.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        assertThat(stored.getEventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(stored.getAggregateId()).isEqualTo("o-1");
        assertThat(stored.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(stored.getRecipientEmail()).isEqualTo("buyer@example.com");
        assertThat(stored.getSubject()).isEqualTo("Order o-1 confirmed");
        assertThat(stored.getBody()).contains("Your order is confirmed.");
    }

    @Test
    void claim_sameKeyAgain_returnsFalseAndKeepsSingleRow() {
        NotificationDelivery first = draft("ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1");
        NotificationDelivery second = draft("ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1");
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
        NotificationDelivery delivery = draft("ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1");
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
        NotificationDelivery delivery = draft("ORDER_CONFIRMED:o-1", "ORDER_CONFIRMED", "o-1");
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

    @Test
    void findDue_returnsPendingInCreatedOrder() {
        inTx(() -> adapter.claim(draft("ORDER_CONFIRMED:a", "ORDER_CONFIRMED", "a")));
        inTx(() -> adapter.claim(draft("REFUND_REQUESTED:b", "REFUND_REQUESTED", "b")));

        List<NotificationDelivery> due = inTx(() -> adapter.findDue(10));

        assertThat(due).extracting(NotificationDelivery::getIdempotencyKey)
                .containsExactly("ORDER_CONFIRMED:a", "REFUND_REQUESTED:b");
    }

    @Test
    void findDue_excludesSentRows() {
        inTx(() -> adapter.claim(draft("ORDER_CONFIRMED:a", "ORDER_CONFIRMED", "a")));
        inTx(() -> {
            adapter.updateStatus("ORDER_CONFIRMED:a", NotificationDeliveryStatus.SENT, null);
            return null;
        });

        List<NotificationDelivery> due = inTx(() -> adapter.findDue(10));

        assertThat(due).isEmpty();
    }

    @Test
    void findDue_returnsFailedRowsBelowMaxAttempts() {
        inTx(() -> adapter.claim(draft("ORDER_CONFIRMED:a", "ORDER_CONFIRMED", "a")));
        inTx(() -> {
            adapter.updateStatus("ORDER_CONFIRMED:a", NotificationDeliveryStatus.FAILED, "boom");
            return null;
        });

        List<NotificationDelivery> due = inTx(() -> adapter.findDue(10));

        assertThat(due).extracting(NotificationDelivery::getIdempotencyKey)
                .containsExactly("ORDER_CONFIRMED:a");
        assertThat(due.get(0).getAttemptCount()).isEqualTo(2);
    }

    @Test
    void findDue_excludesFailedRowsPastMaxAttempts() {
        inTx(() -> adapter.claim(draft("ORDER_CONFIRMED:a", "ORDER_CONFIRMED", "a")));
        inTx(() -> {
            adapter.updateStatus("ORDER_CONFIRMED:a", NotificationDeliveryStatus.FAILED, "boom");
            return null;
        });
        inTx(() -> {
            adapter.updateStatus("ORDER_CONFIRMED:a", NotificationDeliveryStatus.FAILED, "boom");
            return null;
        });

        List<NotificationDelivery> due = inTx(() -> adapter.findDue(10));

        assertThat(due).isEmpty();
    }

    @Test
    void findDue_excludesRowsWithoutBodySnapshot() {
        NotificationDelivery noBody = NotificationDelivery.reconstitute("id-x", "ORDER_CONFIRMED", "a",
                NotificationChannel.EMAIL, "ORDER_CONFIRMED:no-body", NotificationDeliveryStatus.PENDING,
                1, null, null, null, null, Instant.now(), Instant.now());
        inTx(() -> adapter.claim(noBody));

        List<NotificationDelivery> due = inTx(() -> adapter.findDue(10));

        assertThat(due).isEmpty();
    }

    @Test
    void findDue_respectsLimit() {
        inTx(() -> adapter.claim(draft("ORDER_CONFIRMED:a", "ORDER_CONFIRMED", "a")));
        inTx(() -> adapter.claim(draft("ORDER_CONFIRMED:b", "ORDER_CONFIRMED", "b")));

        List<NotificationDelivery> due = inTx(() -> adapter.findDue(1));

        assertThat(due).extracting(NotificationDelivery::getIdempotencyKey)
                .containsExactly("ORDER_CONFIRMED:a");
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