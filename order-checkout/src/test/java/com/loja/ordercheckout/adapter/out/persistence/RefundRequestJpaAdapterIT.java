package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.application.dto.RefundSearchCriteria;
import com.loja.ordercheckout.application.dto.RefundSort;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
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
        em.createNativeQuery("TRUNCATE TABLE tb_order RESTART IDENTITY CASCADE")
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

    @Test
    void find_withDateRange_returnsInclusiveMatches() {
        inTx(() -> {
            adapter.save(refund("r-1", "o-1", "5.00", "2026-01-02T09:00:00Z"));
            adapter.save(refund("r-2", "o-1", "10.00", "2026-01-03T09:00:00Z"));
            adapter.save(refund("r-3", "o-1", "7.00", "2026-01-04T09:00:00Z"));
            return null;
        });
        RefundSearchCriteria criteria = new RefundSearchCriteria(
                null, null,
                Instant.parse("2026-01-03T00:00:00Z"),
                Instant.parse("2026-01-03T23:59:59Z"),
                RefundSort.REQUESTED_DATE, false);

        var result = inTx(() -> adapter.find(criteria, 0, 20));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).extracting(RefundRequest::getId).containsExactly("r-2");
    }

    @Test
    void find_withCustomerQuery_matchesEmailOrRecipientName() {
        inTx(() -> {
            em.persist(OrderJpaEntity.fromDomain(order("o-1", "ana@example.com", "Ana Souza")));
            em.persist(OrderJpaEntity.fromDomain(order("o-2", "bruno@example.com", "Bruno Lima")));
            adapter.save(refund("r-1", "o-1", "5.00", "2026-01-02T09:00:00Z"));
            adapter.save(refund("r-2", "o-2", "10.00", "2026-01-03T09:00:00Z"));
            return null;
        });
        RefundSearchCriteria byEmail = new RefundSearchCriteria(
                null, "ana@example.com", null, null, RefundSort.REQUESTED_DATE, false);
        RefundSearchCriteria byRecipient = new RefundSearchCriteria(
                null, "bruno", null, null, RefundSort.REQUESTED_DATE, false);
        RefundSearchCriteria noMatch = new RefundSearchCriteria(
                null, "ghost", null, null, RefundSort.REQUESTED_DATE, false);

        var emailResult = inTx(() -> adapter.find(byEmail, 0, 20));
        var recipientResult = inTx(() -> adapter.find(byRecipient, 0, 20));
        var noMatchResult = inTx(() -> adapter.find(noMatch, 0, 20));

        assertThat(emailResult.items()).extracting(RefundRequest::getId).containsExactly("r-1");
        assertThat(recipientResult.items()).extracting(RefundRequest::getId).containsExactly("r-2");
        assertThat(noMatchResult.totalElements()).isZero();
    }

    @Test
    void find_sortByAmount_ordersAscendingAndDescending() {
        inTx(() -> {
            adapter.save(refund("r-1", "o-1", "5.00", "2026-01-02T09:00:00Z"));
            adapter.save(refund("r-2", "o-1", "10.00", "2026-01-03T09:00:00Z"));
            adapter.save(refund("r-3", "o-1", "7.00", "2026-01-04T09:00:00Z"));
            return null;
        });
        RefundSearchCriteria ascending = new RefundSearchCriteria(
                null, null, null, null, RefundSort.AMOUNT, true);
        RefundSearchCriteria descending = new RefundSearchCriteria(
                null, null, null, null, RefundSort.AMOUNT, false);

        var ascResult = inTx(() -> adapter.find(ascending, 0, 20));
        var descResult = inTx(() -> adapter.find(descending, 0, 20));

        assertThat(ascResult.items()).extracting(RefundRequest::getAmount)
                .containsExactly(new Money(new BigDecimal("5.00")),
                        new Money(new BigDecimal("7.00")), new Money(new BigDecimal("10.00")));
        assertThat(descResult.items()).extracting(RefundRequest::getAmount)
                .containsExactly(new Money(new BigDecimal("10.00")),
                        new Money(new BigDecimal("7.00")), new Money(new BigDecimal("5.00")));
    }

    private static RefundRequest refund(String id, String orderId, String amount, String createdAt) {
        return RefundRequest.reconstitute(id, orderId, new Money(new BigDecimal(amount)), "Reason",
                RefundStatus.PENDING, null, Instant.parse(createdAt), null);
    }

    private static Order order(String id, String customerEmail, String recipientName) {
        Order order = new Order(id, "u-1", customerEmail);
        order.setShippingAddress(new ShippingAddress(recipientName, "Rua das Flores", "10", null,
                "Centro", "Sao Paulo", "SP", "01310-100", null));
        return order;
    }
}
