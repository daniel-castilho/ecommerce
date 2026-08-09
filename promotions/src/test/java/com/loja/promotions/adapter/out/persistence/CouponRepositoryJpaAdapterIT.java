package com.loja.promotions.adapter.out.persistence;

import com.loja.promotions.application.dto.PageResult;
import com.loja.promotions.domain.model.Coupon;
import com.loja.promotions.domain.model.CouponType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponRepositoryJpaAdapterIT extends AbstractIntegrationTest {

    private CouponRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new CouponRepositoryAdapter();
        adapter.em = em;
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE tb_coupon RESTART IDENTITY CASCADE")
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
    void saveAndFindByCode_roundTrips() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null);
        inTx(() -> adapter.save(coupon));

        Optional<Coupon> restored = inTx(() -> adapter.findByCode("SAVE10"));

        assertThat(restored).isPresent();
        assertThat(restored.get().getId()).isEqualTo(coupon.getId());
        assertThat(restored.get().getCode()).isEqualTo("SAVE10");
        assertThat(restored.get().getType()).isEqualTo(CouponType.PERCENT);
        assertThat(restored.get().getValue()).isEqualByComparingTo("10");
        assertThat(restored.get().isActive()).isTrue();
        assertThat(restored.get().getUsedCount()).isZero();
        assertThat(restored.get().getCreatedAt()).isNotNull();
    }

    @Test
    void saveAndFindById_roundTrips() {
        Coupon coupon = Coupon.create("FIXED25", CouponType.FIXED,
                new BigDecimal("25.00"), true,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z"), 10);
        inTx(() -> adapter.save(coupon));

        Optional<Coupon> restored = inTx(() -> adapter.findById(coupon.getId()));

        assertThat(restored).isPresent();
        assertThat(restored.get().getValidFrom()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(restored.get().getMaxTotalUses()).isEqualTo(10);
        assertThat(restored.get().getUsedCount()).isZero();
    }

    @Test
    void findByCode_unknownCode_returnsEmpty() {
        Optional<Coupon> result = inTx(() -> adapter.findByCode("NOPE"));

        assertThat(result).isEmpty();
    }

    @Test
    void uniqueCode_isEnforcedByDatabase() {
        Coupon first = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null);
        Coupon duplicate = Coupon.reconstitute(first.getId() + "-x", "SAVE10", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null, 0, Instant.now());
        inTx(() -> adapter.save(first));

        assertThatThrownBy(() -> inTx(() -> adapter.save(duplicate)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void search_filtersByCodeFragmentAndActive() {
        inTx(() -> {
            adapter.save(Coupon.create("SAVE10", CouponType.PERCENT,
                    new BigDecimal("10"), true, null, null, null));
            adapter.save(Coupon.create("SAVE20", CouponType.PERCENT,
                    new BigDecimal("20"), false, null, null, null));
            adapter.save(Coupon.create("FREESHIP", CouponType.FIXED,
                    new BigDecimal("30"), true, null, null, null));
            return null;
        });

        PageResult<Coupon> active = inTx(() -> adapter.search(null, true, 0, 20));
        PageResult<Coupon> byFragment = inTx(() -> adapter.search("save", null, 0, 20));
        PageResult<Coupon> both = inTx(() -> adapter.search("SAVE", true, 0, 20));

        assertThat(active.items()).extracting(Coupon::getCode).containsExactlyInAnyOrder("SAVE10", "FREESHIP");
        assertThat(byFragment.items()).extracting(Coupon::getCode).containsExactlyInAnyOrder("SAVE10", "SAVE20");
        assertThat(both.items()).extracting(Coupon::getCode).containsExactly("SAVE10");
    }

    @Test
    void search_noFilters_returnsAllNewestFirst() {
        Coupon older = Coupon.reconstitute("c-1", "OLD", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null, 0, Instant.parse("2026-01-01T00:00:00Z"));
        Coupon newer = Coupon.reconstitute("c-2", "NEW", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null, 0, Instant.parse("2026-01-02T00:00:00Z"));
        inTx(() -> {
            adapter.save(older);
            adapter.save(newer);
            return null;
        });

        PageResult<Coupon> result = inTx(() -> adapter.search(null, null, 0, 20));

        assertThat(result.items()).extracting(Coupon::getCode).containsExactly("NEW", "OLD");
    }

    @Test
    void save_incrementsUsedCountAcrossMerges() {
        Coupon coupon = Coupon.create("LIMITED", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, 3);
        inTx(() -> adapter.save(coupon));
        coupon.recordUsage();
        inTx(() -> adapter.save(coupon));

        Coupon restored = inTx(() -> adapter.findByCode("LIMITED")).orElseThrow();
        assertThat(restored.getUsedCount()).isEqualTo(1);
    }
}
