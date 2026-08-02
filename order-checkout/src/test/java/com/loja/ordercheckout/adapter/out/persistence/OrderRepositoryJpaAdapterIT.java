package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderItem;
import com.loja.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositoryJpaAdapterIT extends AbstractIntegrationTest {

    private OrderRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new OrderRepositoryAdapter();
        adapter.em = em;
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE tb_order_item, tb_order RESTART IDENTITY CASCADE")
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

    /**
     * Runs an operation against the repository inside an explicit transaction, mirroring the
     * container-managed transaction the adapter sees in production (docs/lessons.md #2).
     */
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

    private Order confirmedOrder(String id, String userId) {
        Order order = new Order(id, userId);
        order.addItem(new OrderItem("p1", 2, new Money(new BigDecimal("10.00"))));
        order.addItem(new OrderItem("p2", 3, new Money(new BigDecimal("5.50"))));
        order.confirm();
        return order;
    }

    @Test
    void shouldPersistAndRoundTripConfirmedOrder() {
        Order order = confirmedOrder("order-1", "user-1");

        inTx(() -> adapter.save(order));

        Optional<Order> restored = inTx(() -> adapter.findById("order-1"));
        assertThat(restored).isPresent();
        Order found = restored.get();
        assertThat(found.getUserId()).isEqualTo("user-1");
        assertThat(found.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        assertThat(found.getItems()).hasSize(2);
        assertThat(found.getItems().get(0).getProductId()).isEqualTo("p1");
        assertThat(found.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(found.getItems().get(0).getUnitPrice()).isEqualTo(new Money(new BigDecimal("10.00")));
        assertThat(found.getItems().get(1).getUnitPrice()).isEqualTo(new Money(new BigDecimal("5.50")));
        assertThat(found.getTotal().getAmount()).isEqualByComparingTo("36.50");
    }

    @Test
    void shouldRoundTripCancelledOrder() {
        Order order = new Order("order-2", "user-2");
        order.addItem(new OrderItem("p1", 1, new Money(new BigDecimal("7.25"))));
        order.cancel();

        inTx(() -> adapter.save(order));

        Optional<Order> restored = inTx(() -> adapter.findById("order-2"));
        assertThat(restored).isPresent();
        assertThat(restored.get().getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(restored.get().getItems()).hasSize(1);
    }

    @Test
    void shouldFindByIdReturnEmptyForUnknownId() {
        Optional<Order> restored = inTx(() -> adapter.findById("does-not-exist"));

        assertThat(restored).isEmpty();
    }

    @Test
    void shouldPersistOrdersWithDistinctIds() {
        inTx(() -> adapter.save(confirmedOrder("order-3", "user-3")));
        inTx(() -> adapter.save(confirmedOrder("order-4", "user-4")));

        Optional<Order> first = inTx(() -> adapter.findById("order-3"));
        Optional<Order> second = inTx(() -> adapter.findById("order-4"));

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get().getUserId()).isEqualTo("user-3");
        assertThat(second.get().getUserId()).isEqualTo("user-4");
        assertThat(first.get().getItems()).hasSize(2);
    }
}
