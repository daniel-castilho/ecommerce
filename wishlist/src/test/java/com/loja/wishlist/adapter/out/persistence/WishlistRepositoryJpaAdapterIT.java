package com.loja.wishlist.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.wishlist.domain.exception.DuplicateWishlistItemException;
import com.loja.wishlist.domain.model.WishlistItem;

import jakarta.persistence.EntityTransaction;

class WishlistRepositoryJpaAdapterIT extends AbstractIntegrationTest {

    private WishlistRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new WishlistRepositoryAdapter();
        adapter.em = em;
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE tb_wishlist_item RESTART IDENTITY CASCADE").executeUpdate();
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
    void shouldSaveAndFindByUserAndProduct() {
        WishlistItem item = WishlistItem.create("u-1", "p-1");

        WishlistItem saved = inTx(() -> adapter.save(item));

        assertThat(saved.getId()).isEqualTo(item.getId());
        assertThat(saved.getUserId()).isEqualTo("u-1");
        assertThat(saved.getProductId()).isEqualTo("p-1");
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<WishlistItem> found = inTx(() -> adapter.findByUserAndProduct("u-1", "p-1"));
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(item.getId());
        assertThat(java.time.Duration.between(found.get().getCreatedAt(), item.getCreatedAt()).abs())
                .isLessThan(java.time.Duration.ofNanos(1000));
    }

    @Test
    void findByUserAndProduct_whenAbsent_returnsEmpty() {
        assertThat(inTx(() -> adapter.findByUserAndProduct("u-99", "p-99"))).isEmpty();
    }

    // ------------------------------------------------------------------ uniqueness

    @Test
    void shouldRejectSecondItemForSameUserAndProduct() {
        WishlistItem first = WishlistItem.create("u-1", "p-1");
        WishlistItem duplicate = WishlistItem.reconstitute(
                "w-dup", "u-1", "p-1", Instant.parse("2026-08-02T00:00:00Z"));

        inTx(() -> adapter.save(first));

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            assertThatThrownBy(() -> adapter.save(duplicate))
                    .isInstanceOf(DuplicateWishlistItemException.class)
                    .hasMessageContaining("u-1")
                    .hasMessageContaining("p-1");
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }
    }

    @Test
    void shouldAllowSameUserDifferentProduct() {
        WishlistItem a = WishlistItem.create("u-1", "p-1");
        WishlistItem b = WishlistItem.create("u-1", "p-2");

        inTx(() -> {
            adapter.save(a);
            adapter.save(b);
            return null;
        });

        assertThat(inTx(() -> adapter.exists("u-1", "p-1"))).isTrue();
        assertThat(inTx(() -> adapter.exists("u-1", "p-2"))).isTrue();
    }

    @Test
    void shouldAllowSameProductDifferentUser() {
        WishlistItem a = WishlistItem.create("u-1", "p-1");
        WishlistItem b = WishlistItem.create("u-2", "p-1");

        inTx(() -> {
            adapter.save(a);
            adapter.save(b);
            return null;
        });

        assertThat(inTx(() -> adapter.exists("u-1", "p-1"))).isTrue();
        assertThat(inTx(() -> adapter.exists("u-2", "p-1"))).isTrue();
    }

    // ------------------------------------------------------------------ exists

    @Test
    void exists_shouldBeFalseWhenAbsent() {
        assertThat(inTx(() -> adapter.exists("u-99", "p-99"))).isFalse();
    }

    @Test
    void exists_shouldBeTrueAfterSave() {
        inTx(() -> adapter.save(WishlistItem.create("u-1", "p-1")));

        assertThat(inTx(() -> adapter.exists("u-1", "p-1"))).isTrue();
    }

    // ------------------------------------------------------------------ list newest first

    @Test
    void findByUserId_shouldReturnNewestFirstAndOnlyOwnerItems() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        WishlistItem older = WishlistItem.reconstitute(
                "w-old", "u-1", "p-1", base.plusSeconds(10));
        WishlistItem newer = WishlistItem.reconstitute(
                "w-new", "u-1", "p-2", base.plusSeconds(30));
        WishlistItem otherUser = WishlistItem.reconstitute(
                "w-other", "u-2", "p-3", base.plusSeconds(40));

        inTx(() -> {
            adapter.save(older);
            adapter.save(newer);
            adapter.save(otherUser);
            return null;
        });

        List<WishlistItem> items = inTx(() -> adapter.findByUserIdOrderByCreatedAtDesc("u-1"));
        assertThat(items).extracting(WishlistItem::getId).containsExactly("w-new", "w-old");
    }

    @Test
    void findByUserId_whenEmpty_returnsEmptyList() {
        assertThat(inTx(() -> adapter.findByUserIdOrderByCreatedAtDesc("u-empty"))).isEmpty();
    }

    // ------------------------------------------------------------------ delete (idempotent)

    @Test
    void deleteByUserAndProduct_shouldRemoveExistingItem() {
        inTx(() -> adapter.save(WishlistItem.create("u-1", "p-1")));
        assertThat(inTx(() -> adapter.exists("u-1", "p-1"))).isTrue();

        inTx(() -> adapter.deleteByUserAndProduct("u-1", "p-1"));

        assertThat(inTx(() -> adapter.exists("u-1", "p-1"))).isFalse();
        assertThat(inTx(() -> adapter.findByUserAndProduct("u-1", "p-1"))).isEmpty();
    }

    @Test
    void deleteByUserAndProduct_whenMissing_isIdempotentNoOp() {
        inTx(() -> adapter.deleteByUserAndProduct("u-missing", "p-missing"));

        assertThat(inTx(() -> adapter.exists("u-missing", "p-missing"))).isFalse();
    }

    @Test
    void deleteByUserAndProduct_shouldNotAffectOtherUsersOrProducts() {
        inTx(() -> {
            adapter.save(WishlistItem.create("u-1", "p-1"));
            adapter.save(WishlistItem.create("u-1", "p-2"));
            adapter.save(WishlistItem.create("u-2", "p-1"));
            return null;
        });

        inTx(() -> adapter.deleteByUserAndProduct("u-1", "p-1"));

        assertThat(inTx(() -> adapter.exists("u-1", "p-1"))).isFalse();
        assertThat(inTx(() -> adapter.exists("u-1", "p-2"))).isTrue();
        assertThat(inTx(() -> adapter.exists("u-2", "p-1"))).isTrue();
    }
}
