package com.loja.productcatalog.adapter.out.persistence;

import com.loja.productcatalog.application.dto.ReservationRequest;
import com.loja.productcatalog.domain.exception.InsufficientStockException;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link InventoryReservationJpaAdapter} against a real
 * Postgres (Testcontainers). Verifies that reservations hold stock, confirm
 * keeps the decrement, release/expiry give it back, and that two concurrent
 * reserves of the last units cannot oversell.
 */
class InventoryReservationJpaAdapterIT extends AbstractIntegrationTest {

    private ProductRepositoryAdapter productRepository;
    private InventoryReservationJpaAdapter reservationAdapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        productRepository = new ProductRepositoryAdapter();
        productRepository.em = em;
        reservationAdapter = new InventoryReservationJpaAdapter();
        reservationAdapter.em = em;
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE inventory_reservation, " +
                        "tb_product_image, tb_product_category, tb_product RESTART IDENTITY CASCADE")
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

    private void save(Product product) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        productRepository.save(product);
        tx.commit();
        em.clear();
    }

    private Product product(String id, int stock) {
        return new Product(id, new Sku("SKU-" + id), new Slug("slug-" + id), "Product " + id,
                null, null, new Money(new BigDecimal("10.00")), null, stock,
                ProductStatus.ACTIVE, null, null, null, Set.of(1L), List.of());
    }

    private int stockOf(String productId) {
        return inTx(() -> productRepository.findById(productId).orElseThrow().getStock());
    }

    private long holdCount(String reservationId) {
        return inTx(() -> (Long) em.createNativeQuery(
                        "SELECT COUNT(*) FROM inventory_reservation WHERE reservation_id = :id")
                .setParameter("id", reservationId)
                .getSingleResult());
    }

    @Test
    void reserve_reducesAvailableStockAndRecordsHold() {
        save(product("p-1", 10));

        inTx(() -> {
            reservationAdapter.reserve("r1", List.of(new ReservationRequest("p-1", 3)));
            return null;
        });

        assertThat(stockOf("p-1")).isEqualTo(7);
        assertThat(holdCount("r1")).isEqualTo(1);
    }

    @Test
    void reserve_insufficientStock_throwsAndChangesNothing() {
        save(product("p-1", 2));

        assertThatThrownBy(() -> inTx(() -> {
            reservationAdapter.reserve("r1", List.of(new ReservationRequest("p-1", 5)));
            return null;
        })).isInstanceOf(InsufficientStockException.class);

        assertThat(stockOf("p-1")).isEqualTo(2);
        assertThat(holdCount("r1")).isZero();
    }

    @Test
    void reserve_duplicateReservationId_isNoOp() {
        save(product("p-1", 10));
        List<ReservationRequest> items = List.of(new ReservationRequest("p-1", 3));

        inTx(() -> {
            reservationAdapter.reserve("r1", items);
            return null;
        });
        inTx(() -> {
            reservationAdapter.reserve("r1", items);
            return null;
        });

        assertThat(stockOf("p-1")).isEqualTo(7);
        assertThat(holdCount("r1")).isEqualTo(1);
    }

    @Test
    void confirm_keepsStockDecrementedAndRemovesHold() {
        save(product("p-1", 10));
        inTx(() -> {
            reservationAdapter.reserve("r1", List.of(new ReservationRequest("p-1", 3)));
            return null;
        });

        inTx(() -> {
            reservationAdapter.confirm("r1");
            return null;
        });

        assertThat(stockOf("p-1")).isEqualTo(7);
        assertThat(holdCount("r1")).isZero();
    }

    @Test
    void release_restoresStockAndRemovesHold() {
        save(product("p-1", 10));
        inTx(() -> {
            reservationAdapter.reserve("r1", List.of(new ReservationRequest("p-1", 3)));
            return null;
        });

        inTx(() -> {
            reservationAdapter.release("r1");
            return null;
        });

        assertThat(stockOf("p-1")).isEqualTo(10);
        assertThat(holdCount("r1")).isZero();
    }

    @Test
    void release_unknownReservation_isNoOp() {
        save(product("p-1", 10));

        inTx(() -> {
            reservationAdapter.release("unknown");
            return null;
        });

        assertThat(stockOf("p-1")).isEqualTo(10);
    }

    @Test
    void reserve_afterExpiredHold_releasesExpiredUnitsFirst() {
        save(product("p-1", 10));
        em.getTransaction().begin();
        em.persist(new InventoryReservationJpaEntity(
                "expired-1", "p-1", 2, Instant.now().minusSeconds(60)));
        em.getTransaction().commit();
        em.clear();

        inTx(() -> {
            reservationAdapter.reserve("r2", List.of(new ReservationRequest("p-1", 4)));
            return null;
        });

        assertThat(stockOf("p-1")).isEqualTo(8);
        assertThat(holdCount("expired-1")).isZero();
        assertThat(holdCount("r2")).isEqualTo(1);
    }

    @Test
    void reserve_twoConcurrentReservationsForLastUnits_onlyOneSucceeds() throws Exception {
        save(product("p-race", 1));

        int poolSize = 2;
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch ready = new CountDownLatch(poolSize);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < poolSize; i++) {
            final int worker = i;
            futures.add(executor.submit(() -> {
                EntityManager workerEm = emf.createEntityManager();
                InventoryReservationJpaAdapter workerAdapter = new InventoryReservationJpaAdapter();
                workerAdapter.em = workerEm;
                try {
                    ready.countDown();
                    go.await();
                    EntityTransaction workerTx = workerEm.getTransaction();
                    workerTx.begin();
                    workerAdapter.reserve("race-" + worker,
                            List.of(new ReservationRequest("p-race", 1)));
                    workerTx.commit();
                    return true;
                } catch (InsufficientStockException e) {
                    if (workerEm.getTransaction().isActive()) {
                        workerEm.getTransaction().rollback();
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
        assertThat(stockOf("p-race")).isZero();
        assertThat(holdCount("race-0") + holdCount("race-1")).isEqualTo(1);
    }
}
