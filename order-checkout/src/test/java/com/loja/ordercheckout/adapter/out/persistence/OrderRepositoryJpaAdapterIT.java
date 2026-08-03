package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.exception.OrderConcurrentModificationException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.PaymentAuthorization;
import com.loja.ordercheckout.domain.model.PaymentCapture;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    private OrderLine line(String productId, String productName, int quantity, Money unitPrice, int position) {
        return new OrderLine(productId, productName, unitPrice, quantity, position);
    }

    private Order confirmedOrder(String id, String userId) {
        Order order = new Order(id, userId, "user@example.com");
        order.addItem(line("p1", "Product A", 2, new Money(new BigDecimal("10.00")), 0));
        order.addItem(line("p2", "Product B", 3, new Money(new BigDecimal("5.50")), 1));
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
        assertThat(found.getCustomerEmail()).isEqualTo("user@example.com");
        assertThat(found.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
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
        order.addItem(line("p1", "Product A", 1, new Money(new BigDecimal("7.25")), 0));
        order.cancel();

        inTx(() -> adapter.save(order));

        Optional<Order> restored = inTx(() -> adapter.findById("order-2"));
        assertThat(restored).isPresent();
        assertThat(restored.get().getStatus()).isEqualTo(OrderStatus.CANCELLED);
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

    @Test
    void shouldFindByCustomerIdPaginateAndSortNewestFirst() {
        Instant base = Instant.now();
        for (int i = 0; i < 50; i++) {
            final int index = i;
            inTx(() -> adapter.save(orderAt("paged-" + index, "customer-1",
                    base.plusSeconds(index), OrderStatus.CONFIRMED)));
        }
        inTx(() -> adapter.save(orderAt("other-1", "customer-2",
                base.plusSeconds(100), OrderStatus.CONFIRMED)));

        PageResult<Order> firstPage = inTx(() -> adapter.findByCustomerId("customer-1", 0, 20));
        assertThat(firstPage.items()).hasSize(20);
        assertThat(firstPage.totalElements()).isEqualTo(50);
        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(firstPage.items().get(0).getId()).isEqualTo("paged-49");

        PageResult<Order> lastPage = inTx(() -> adapter.findByCustomerId("customer-1", 2, 20));
        assertThat(lastPage.items()).hasSize(10);
        assertThat(lastPage.items().get(0).getId()).isEqualTo("paged-9");

        PageResult<Order> empty = inTx(() -> adapter.findByCustomerId("unknown", 0, 20));
        assertThat(empty.items()).isEmpty();
        assertThat(empty.totalElements()).isZero();
    }

    @Test
    void shouldClampPaginationParams() {
        Instant base = Instant.now();
        for (int i = 0; i < 5; i++) {
            final int index = i;
            inTx(() -> adapter.save(orderAt("clamp-" + index, "customer-1",
                    base.plusSeconds(index), OrderStatus.PENDING)));
        }

        PageResult<Order> zeroSize = inTx(() -> adapter.findByCustomerId("customer-1", 0, 0));
        assertThat(zeroSize.items()).hasSize(5);
        assertThat(zeroSize.pageSize()).isEqualTo(PageResult.DEFAULT_PAGE_SIZE);

        PageResult<Order> oversized = inTx(() -> adapter.findByCustomerId("customer-1", 0, 1000));
        assertThat(oversized.pageSize()).isEqualTo(PageResult.MAX_PAGE_SIZE);
    }

    private Order orderAt(String id, String userId, Instant createdAt, OrderStatus status) {
        return Order.restore(id, userId, "customer@example.com", createdAt, status,
                List.of(), null, null, null, null, createdAt);
    }

    private Order orderAt(String id, String userId, Instant createdAt, OrderStatus status,
                          List<OrderLine> lines, Money shippingCost) {
        return Order.restore(id, userId, "customer@example.com", createdAt, status,
                lines, null, shippingCost, null, null, createdAt);
    }


    @Test
    void shouldFindByStatusReturnOnlyMatchingOrders() {
        inTx(() -> adapter.save(confirmedOrder("order-10", "user-10")));

        Order pending = new Order("order-11", "user-11");
        pending.addItem(line("p1", "Product A", 1, new Money(new BigDecimal("7.25")), 0));
        inTx(() -> adapter.save(pending));

        Order shipped = new Order("order-12", "user-12");
        shipped.addItem(line("p1", "Product A", 1, new Money(new BigDecimal("7.25")), 0));
        shipped.confirm();
        shipped.process();
        shipped.ship("TRACK-1");
        inTx(() -> adapter.save(shipped));

        List<Order> confirmed = inTx(() -> adapter.findByStatus(OrderStatus.CONFIRMED));

        assertThat(confirmed).hasSize(1);
        assertThat(confirmed.get(0).getId()).isEqualTo("order-10");
    }

    @Test
    void shouldCountAllOrders() {
        inTx(() -> adapter.save(confirmedOrder("count-1", "user-1")));
        inTx(() -> adapter.save(confirmedOrder("count-2", "user-2")));
        inTx(() -> adapter.save(confirmedOrder("count-3", "user-3")));

        long total = inTx(adapter::countAll);

        assertThat(total).isEqualTo(3);
    }

    @Test
    void shouldCountAllOrdersReturnZeroWhenEmpty() {
        long total = inTx(adapter::countAll);

        assertThat(total).isZero();
    }

    @Test
    void shouldSumRevenueSinceExcludingCancelledAndRefunded() {
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        inTx(() -> adapter.save(orderAt("rev-1", "u1", today, OrderStatus.CONFIRMED,
                List.of(line("p1", "A", 2, new Money(new BigDecimal("10.00")), 0)),
                new Money(new BigDecimal("5.00")))));
        inTx(() -> adapter.save(orderAt("rev-2", "u2", today, OrderStatus.PENDING,
                List.of(line("p1", "A", 1, new Money(new BigDecimal("7.00")), 0)), Money.zero())));
        inTx(() -> adapter.save(orderAt("rev-3", "u3", today, OrderStatus.CANCELLED,
                List.of(line("p1", "A", 3, new Money(new BigDecimal("10.00")), 0)), Money.zero())));
        inTx(() -> adapter.save(orderAt("rev-4", "u4", today.minus(2, ChronoUnit.DAYS), OrderStatus.CONFIRMED,
                List.of(line("p1", "A", 1, new Money(new BigDecimal("99.00")), 0)), Money.zero())));

        Money revenue = inTx(() -> adapter.revenueSince(today));

        assertThat(revenue).isEqualTo(new Money(new BigDecimal("32.00")));
    }

    @Test
    void shouldSumRevenueSinceReturnZeroWhenNoMatchingOrders() {
        Instant yesterday = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS);
        inTx(() -> adapter.save(orderAt("rev-empty", "u1", yesterday, OrderStatus.CONFIRMED,
                List.of(line("p1", "A", 1, new Money(new BigDecimal("10.00")), 0)), Money.zero())));

        Money revenue = inTx(() -> adapter.revenueSince(Instant.now().truncatedTo(ChronoUnit.DAYS)));

        assertThat(revenue).isEqualTo(Money.zero());
    }

    @Test
    void shouldCountOrdersCreatedSince() {
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        inTx(() -> adapter.save(orderAt("cnt-1", "u1", today, OrderStatus.CONFIRMED, List.of(), Money.zero())));
        inTx(() -> adapter.save(orderAt("cnt-2", "u2", today.plusSeconds(60), OrderStatus.PENDING, List.of(), Money.zero())));
        inTx(() -> adapter.save(orderAt("cnt-3", "u3", today.minus(3, ChronoUnit.DAYS), OrderStatus.CONFIRMED, List.of(), Money.zero())));

        long count = inTx(() -> adapter.countCreatedSince(today));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldCountOrdersByStatusWithZeroFill() {
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        inTx(() -> adapter.save(orderAt("st-1", "u1", today, OrderStatus.CONFIRMED, List.of(), Money.zero())));
        inTx(() -> adapter.save(orderAt("st-2", "u2", today, OrderStatus.CONFIRMED, List.of(), Money.zero())));
        inTx(() -> adapter.save(orderAt("st-3", "u3", today, OrderStatus.PENDING, List.of(), Money.zero())));

        Map<OrderStatus, Long> counts = inTx(adapter::countByStatus);

        assertThat(counts.get(OrderStatus.CONFIRMED)).isEqualTo(2);
        assertThat(counts.get(OrderStatus.PENDING)).isEqualTo(1);
        assertThat(counts.get(OrderStatus.DELIVERED)).isZero();
        assertThat(counts).hasSize(OrderStatus.values().length);
    }

    @Test
    void shouldPersistLinesSortedByPosition() {
        Order order = new Order("order-20", "user-20");
        order.addItem(line("c", "Product C", 1, new Money(new BigDecimal("1.00")), 2));
        order.addItem(line("a", "Product A", 1, new Money(new BigDecimal("1.00")), 0));
        order.addItem(line("b", "Product B", 1, new Money(new BigDecimal("1.00")), 1));
        order.confirm();

        inTx(() -> adapter.save(order));

        Optional<Order> restored = inTx(() -> adapter.findById("order-20"));
        assertThat(restored).isPresent();
        List<OrderLine> items = restored.get().getItems();
        assertThat(items).extracting(OrderLine::getPosition).containsExactly(0, 1, 2);
        assertThat(items).extracting(OrderLine::getProductId).containsExactly("a", "b", "c");
    }

    @Test
    void shouldUpdateExistingOrderInsteadOfDuplicating() {
        Order order = confirmedOrder("order-30", "user-30");

        inTx(() -> adapter.save(order));

        Order loaded = inTx(() -> adapter.findById("order-30")).orElseThrow();
        loaded.process();
        inTx(() -> adapter.save(loaded));

        Optional<Order> restored = inTx(() -> adapter.findById("order-30"));
        assertThat(restored).isPresent();
        assertThat(restored.get().getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(restored.get().getVersion()).isGreaterThan(0);

        Long rowCount = inTx(() -> (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM tb_order WHERE id = 'order-30'").getSingleResult());
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void optimisticLock_twoConcurrentUpdatesOnlyOneWins() throws Exception {
        inTx(() -> adapter.save(confirmedOrder("lock-1", "user-1")));

        int poolSize = 2;
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch ready = new CountDownLatch(poolSize);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < poolSize; i++) {
            futures.add(executor.submit(() -> {
                EntityManager workerEm = emf.createEntityManager();
                OrderRepositoryAdapter workerAdapter = new OrderRepositoryAdapter();
                workerAdapter.em = workerEm;
                EntityTransaction tx = workerEm.getTransaction();
                tx.begin();
                try {
                    Order order = workerAdapter.findById("lock-1").orElseThrow();
                    ready.countDown();
                    go.await();
                    order.cancel();
                    workerAdapter.save(order);
                    tx.commit();
                    return true;
                } catch (OrderConcurrentModificationException e) {
                    if (tx.isActive()) {
                        tx.rollback();
                    }
                    return false;
                } finally {
                    workerEm.close();
                }
            }));
        }

        ready.await();
        go.countDown();
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(results).containsExactlyInAnyOrder(true, false);

        Optional<Order> restored = inTx(() -> adapter.findById("lock-1"));
        assertThat(restored).isPresent();
        assertThat(restored.get().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        Long rowCount = inTx(() -> (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM tb_order WHERE id = 'lock-1'").getSingleResult());
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void shouldRoundTripFullAggregateWithAddressAndPayment() {        Order order = new Order("order-40", "user-40");
        order.addItem(line("p1", "Product A", 2, new Money(new BigDecimal("10.00")), 0));
        order.setShippingAddress(new ShippingAddress("Ana Souza", "Rua das Flores", "123", null,
                "Centro", "São Paulo", "SP", "01310-100", null));
        order.setShippingCost(new Money(new BigDecimal("15.00")));
        order.authorize(new PaymentAuthorization("card", "auth-1",
                new Money(new BigDecimal("35.00")), "tx-1", Instant.now()));
        order.capture(new PaymentCapture("auth-1", "capture-1",
                new Money(new BigDecimal("35.00")), "tx-1", Instant.now()));

        inTx(() -> adapter.save(order));

        Optional<Order> restored = inTx(() -> adapter.findById("order-40"));
        assertThat(restored).isPresent();
        Order found = restored.get();
        assertThat(found.getShippingAddress().getPostalCode()).isEqualTo("01310-100");
        assertThat(found.getShippingCost().getAmount()).isEqualByComparingTo("15.00");
        assertThat(found.getPaymentInfo().getCaptureId()).isEqualTo("capture-1");
        assertThat(found.getPaymentInfo().getCapturedAmount().getAmount()).isEqualByComparingTo("35.00");
        assertThat(found.getTotal().getAmount()).isEqualByComparingTo("35.00");
    }
}
