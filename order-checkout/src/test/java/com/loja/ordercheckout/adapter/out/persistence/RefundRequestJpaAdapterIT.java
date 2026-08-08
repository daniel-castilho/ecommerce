package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RefundRequestJpaAdapterIT extends AbstractIntegrationTest {

    private RefundRequestJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new RefundRequestJpaAdapter();
        adapter.em = em;
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE refund_requests RESTART IDENTITY CASCADE")
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
    void findByOrderId_returnsAllRequestsNewestFirst() {
        RefundRequest older = RefundRequest.reconstitute("r-1", "o-1",
                new Money(new BigDecimal("5.00")), "Too small", RefundStatus.REJECTED,
                "Not eligible", java.time.Instant.parse("2026-01-02T09:00:00Z"),
                java.time.Instant.parse("2026-01-02T10:00:00Z"));
        RefundRequest newer = RefundRequest.reconstitute("r-2", "o-1",
                new Money(new BigDecimal("10.00")), "Wrong size", RefundStatus.PROCESSED,
                null, java.time.Instant.parse("2026-01-03T09:00:00Z"),
                java.time.Instant.parse("2026-01-03T11:00:00Z"));
        RefundRequest otherOrder = RefundRequest.reconstitute("r-3", "o-2",
                new Money(new BigDecimal("7.00")), "Damaged", RefundStatus.PENDING,
                null, java.time.Instant.parse("2026-01-04T09:00:00Z"), null);
        inTx(() -> {
            adapter.save(older);
            adapter.save(newer);
            adapter.save(otherOrder);
            return null;
        });

        List<RefundRequest> result = inTx(() -> adapter.findByOrderId("o-1"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RefundRequest::getId).containsExactly("r-2", "r-1");
        assertThat(result).extracting(RefundRequest::getStatus)
                .containsExactly(RefundStatus.PROCESSED, RefundStatus.REJECTED);
    }

    @Test
    void findByOrderId_returnsEmptyForUnknownOrder() {
        List<RefundRequest> result = inTx(() -> adapter.findByOrderId("missing"));

        assertThat(result).isEmpty();
    }
}
