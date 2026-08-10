package com.loja.ordercheckout.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loja.ordercheckout.domain.exception.CartConcurrentModificationException;
import com.loja.ordercheckout.domain.model.Cart;
import com.loja.ordercheckout.domain.model.CartLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CartRepositoryJpaAdapterIT extends AbstractIntegrationTest {

    private CartRepositoryJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new CartRepositoryJpaAdapter();
        adapter.em = em;
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE tb_cart RESTART IDENTITY CASCADE").executeUpdate();
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
     * Runs an operation inside an explicit transaction, mirroring the
     * container-managed transaction the adapter sees in production
     * (see docs/lessons.md).
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

    private void inTx(Runnable operation) {
        inTx(() -> {
            operation.run();
            return null;
        });
    }

    // ------------------------------------------------------------------ save / find

    @Test
    void shouldSaveNewCartAndFindByUserId() {
        Cart cart = Cart.create("u-1");
        cart.add("p1", 2);
        cart.add("p2", 1);

        Cart saved = inTx(() -> adapter.save(cart));

        assertThat(saved.getId()).isEqualTo(cart.getId());
        assertThat(saved.getUserId()).isEqualTo("u-1");
        assertThat(saved.getLines()).extracting(CartLine::productId).containsExactly("p1", "p2");

        Optional<Cart> found = inTx(() -> adapter.findByUserId("u-1"));
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(cart.getId());
        assertThat(found.get().getLines()).hasSize(2);
        assertThat(found.get().getLines().get(0).productId()).isEqualTo("p1");
        assertThat(found.get().getLines().get(0).quantity()).isEqualTo(2);
        assertThat(found.get().getLines().get(1).productId()).isEqualTo("p2");
    }

    @Test
    void shouldUpsertLineOnSecondSave() {
        Cart cart = Cart.create("u-1");
        cart.add("p1", 1);
        inTx(() -> adapter.save(cart));

        Cart reloaded = inTx(() -> adapter.findByUserId("u-1")).orElseThrow();
        reloaded.add("p1", 3);
        inTx(() -> adapter.save(reloaded));

        Optional<Cart> found = inTx(() -> adapter.findByUserId("u-1"));
        assertThat(found).isPresent();
        assertThat(found.get().getLines()).hasSize(1);
        assertThat(found.get().getLines().get(0).productId()).isEqualTo("p1");
        assertThat(found.get().getLines().get(0).quantity()).isEqualTo(4);
    }

    @Test
    void shouldPersistFreshOptimisticLockVersionAfterSave() {
        Cart cart = Cart.create("u-1");
        cart.add("p1", 1);
        Cart saved = inTx(() -> adapter.save(cart));

        Cart reloaded = inTx(() -> adapter.findByUserId("u-1")).orElseThrow();
        reloaded.add("p2", 1);
        Cart savedAgain = inTx(() -> adapter.save(reloaded));

        assertThat(savedAgain.getVersion()).isGreaterThan(saved.getVersion());
    }

    @Test
    void findByUserId_whenAbsent_returnsEmpty() {
        assertThat(inTx(() -> adapter.findByUserId("u-missing"))).isEmpty();
    }

    // ------------------------------------------------------------------ guest cart (S12)

    @Test
    void shouldRoundTripCartKeyedByGuestId() {
        String guestId = "11111111-2222-3333-4444-555555555555";
        Cart cart = Cart.create(guestId);
        cart.add("p1", 2);

        Cart saved = inTx(() -> adapter.save(cart));

        assertThat(saved.getUserId()).isEqualTo(guestId);
        Optional<Cart> found = inTx(() -> adapter.findByUserId(guestId));
        assertThat(found).isPresent();
        assertThat(found.get().getLines()).hasSize(1);
        assertThat(found.get().getLines().get(0).productId()).isEqualTo("p1");
        assertThat(found.get().getLines().get(0).quantity()).isEqualTo(2);

        inTx(() -> adapter.deleteByUserId(guestId));
        assertThat(inTx(() -> adapter.findByUserId(guestId))).isEmpty();
    }

    @Test
    void shouldKeepGuestAndUserCartsSeparate() {
        String guestId = "11111111-2222-3333-4444-555555555555";
        Cart guest = Cart.create(guestId);
        guest.add("p1", 2);
        Cart user = Cart.create("u-1");
        user.add("p2", 1);
        inTx(() -> adapter.save(guest));
        inTx(() -> adapter.save(user));

        assertThat(inTx(() -> adapter.findByUserId(guestId))).isPresent();
        assertThat(inTx(() -> adapter.findByUserId("u-1"))).isPresent();
    }

    // ------------------------------------------------------------------ one cart per user

    @Test
    void save_secondCartForSameUser_throws() {
        Cart first = Cart.create("u-1");
        inTx(() -> adapter.save(first));

        Cart second = Cart.create("u-1");
        second.add("p1", 1);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            assertThatThrownBy(() -> adapter.save(second))
                    .isInstanceOf(PersistenceException.class);
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }
    }

    // ------------------------------------------------------------------ optimistic lock

    @Test
    void optimisticLock_twoConcurrentUpdatesOnlyOneWins() throws Exception {
        Cart seed = Cart.create("lock-user");
        seed.add("p1", 1);
        inTx(() -> adapter.save(seed));

        int poolSize = 2;
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch ready = new CountDownLatch(poolSize);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < poolSize; i++) {
            String productId = "p-concurrent-" + i;
            futures.add(executor.submit(() -> {
                EntityManager workerEm = emf.createEntityManager();
                CartRepositoryJpaAdapter workerAdapter = new CartRepositoryJpaAdapter();
                workerAdapter.em = workerEm;
                EntityTransaction tx = workerEm.getTransaction();
                tx.begin();
                try {
                    Cart cart = workerAdapter.findByUserId("lock-user").orElseThrow();
                    ready.countDown();
                    go.await();
                    cart.add(productId, 1);
                    workerAdapter.save(cart);
                    tx.commit();
                    return true;
                } catch (CartConcurrentModificationException e) {
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

        Optional<Cart> restored = inTx(() -> adapter.findByUserId("lock-user"));
        assertThat(restored).isPresent();
        assertThat(restored.get().getLines()).hasSize(2);
        assertThat(restored.get().getLines())
                .extracting(CartLine::productId)
                .contains("p1");
        assertThat(restored.get().getLines().stream()
                .filter(line -> line.productId().startsWith("p-concurrent-"))
                .count()).isEqualTo(1);
        assertThat(restored.get().getLines().stream()
                .filter(line -> line.productId().startsWith("p-concurrent-"))
                .findFirst().orElseThrow().quantity()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deleteByUserId_removesCartAndLines() {
        Cart cart = Cart.create("u-1");
        cart.add("p1", 1);
        cart.add("p2", 2);
        inTx(() -> adapter.save(cart));

        inTx(() -> adapter.deleteByUserId("u-1"));

        assertThat(inTx(() -> adapter.findByUserId("u-1"))).isEmpty();
        Long cartRows = inTx(() -> (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM tb_cart WHERE user_id = 'u-1'").getSingleResult());
        Long lineRows = inTx(() -> (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM tb_cart_line").getSingleResult());
        assertThat(cartRows).isZero();
        assertThat(lineRows).isZero();
    }

    @Test
    void deleteByUserId_whenAbsent_isIdempotentNoOp() {
        inTx(() -> adapter.deleteByUserId("u-missing"));

        assertThat(inTx(() -> adapter.findByUserId("u-missing"))).isEmpty();
    }
}
