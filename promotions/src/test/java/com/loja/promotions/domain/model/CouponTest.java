package com.loja.promotions.domain.model;

import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void create_normalizesCodeToUppercaseAndTrims() {
        Coupon coupon = Coupon.create("  save10 ", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null);

        assertThat(coupon.getCode()).isEqualTo("SAVE10");
        assertThat(coupon.getType()).isEqualTo(CouponType.PERCENT);
        assertThat(coupon.getUsedCount()).isZero();
        assertThat(coupon.isActive()).isTrue();
    }

    @Test
    void create_blankCode_throws() {
        assertThatThrownBy(() -> Coupon.create("   ", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void create_percentValueOutsideRange_throws() {
        assertThatThrownBy(() -> Coupon.create("SAVE", CouponType.PERCENT,
                BigDecimal.ZERO, true, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Coupon.create("SAVE", CouponType.PERCENT,
                new BigDecimal("101"), true, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_fixedValueNonPositive_throws() {
        assertThatThrownBy(() -> Coupon.create("SAVE", CouponType.FIXED,
                BigDecimal.ZERO, true, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_maxUsesBelowOne_throws() {
        assertThatThrownBy(() -> Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_validFromAfterValidTo_throws() {
        assertThatThrownBy(() -> Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validFrom");
    }

    @Test
    void create_validWindowOnSameInstant_isAccepted() {
        Instant at = Instant.parse("2026-08-01T00:00:00Z");
        Coupon coupon = Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, at, at, null);

        assertThat(coupon.isApplicableAt(at)).isTrue();
    }

    @Test
    void create_partialWindow_isAccepted() {
        Coupon fromOnly = Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, Instant.parse("2026-08-01T00:00:00Z"), null, null);
        Coupon toOnly = Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, Instant.parse("2026-08-31T00:00:00Z"), null);

        assertThat(fromOnly.getValidFrom()).isNotNull();
        assertThat(fromOnly.getValidTo()).isNull();
        assertThat(toOnly.getValidFrom()).isNull();
        assertThat(toOnly.getValidTo()).isNotNull();
    }

    @Test
    void discountFor_percent_appliesProportionOfSubtotal() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null);

        Money discount = coupon.discountFor(new Money(new BigDecimal("100.00")));

        assertThat(discount.getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void discountFor_percentAtFullValue_coversWholeSubtotal() {
        Coupon coupon = Coupon.create("FREE100", CouponType.PERCENT,
                new BigDecimal("100"), true, null, null, null);

        Money discount = coupon.discountFor(new Money(new BigDecimal("37.50")));

        assertThat(discount.getAmount()).isEqualByComparingTo("37.50");
    }

    @Test
    void discountFor_fixed_capsAtSubtotal() {
        Coupon coupon = Coupon.create("SAVE50", CouponType.FIXED,
                new BigDecimal("50"), true, null, null, null);

        Money capped = coupon.discountFor(new Money(new BigDecimal("30.00")));
        Money full = coupon.discountFor(new Money(new BigDecimal("100.00")));

        assertThat(capped.getAmount()).isEqualByComparingTo("30.00");
        assertThat(full.getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void isApplicableAt_inactiveCoupon_isFalse() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), false, null, null, null);

        assertThat(coupon.isApplicableAt(NOW)).isFalse();
    }

    @Test
    void isApplicableAt_respectsValidityWindow() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT, new BigDecimal("10"),
                true, Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z"),
                null);

        assertThat(coupon.isApplicableAt(Instant.parse("2026-07-31T23:59:59Z"))).isFalse();
        assertThat(coupon.isApplicableAt(Instant.parse("2026-08-15T12:00:00Z"))).isTrue();
        assertThat(coupon.isApplicableAt(Instant.parse("2026-09-01T00:00:00Z"))).isFalse();
    }

    @Test
    void canBeUsed_respectsMaxTotalUses() {
        Coupon coupon = Coupon.create("LIMITED", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, 2);

        assertThat(coupon.canBeUsed(NOW)).isTrue();
        coupon.recordUsage();
        coupon.recordUsage();
        assertThat(coupon.canBeUsed(NOW)).isFalse();
    }

    @Test
    void recordUsage_beyondCap_throws() {
        Coupon coupon = Coupon.create("LIMITED", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, 1);
        coupon.recordUsage();

        assertThatThrownBy(coupon::recordUsage)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("maximum number of uses");
    }

    @Test
    void activateAndDeactivate_toggleFlag() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), false, null, null, null);
        assertThat(coupon.isApplicableAt(NOW)).isFalse();

        coupon.activate();
        assertThat(coupon.isApplicableAt(NOW)).isTrue();

        coupon.deactivate();
        assertThat(coupon.isApplicableAt(NOW)).isFalse();
    }

    // ---- eligibility scope (ALL / PRODUCT / CATEGORY) ----

    @Test
    void create_allScope_withTargets_throws() {
        assertThatThrownBy(() -> Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null,
                CouponScope.ALL, Set.of("p1"), Set.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ALL scope");
    }

    @Test
    void create_productScope_withoutProducts_throws() {
        assertThatThrownBy(() -> Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null,
                CouponScope.PRODUCT, Set.of(), Set.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product id");
    }

    @Test
    void create_categoryScope_withoutCategories_throws() {
        assertThatThrownBy(() -> Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null,
                CouponScope.CATEGORY, Set.of(), Set.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category id");
    }

    @Test
    void create_maxUsesPerUserBelowOne_throws() {
        assertThatThrownBy(() -> Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null,
                CouponScope.ALL, Set.of(), Set.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per user");
    }

    @Test
    void isLineEligible_allScope_acceptsEveryLine() {
        Coupon coupon = Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null);

        assertThat(coupon.isLineEligible("p1", Set.of(1L))).isTrue();
        assertThat(coupon.isLineEligible(null, Set.of())).isTrue();
    }

    @Test
    void isLineEligible_productScope_acceptsOnlyScopedProduct() {
        Coupon coupon = Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null,
                CouponScope.PRODUCT, Set.of("p1"), Set.of(), null);

        assertThat(coupon.isLineEligible("p1", Set.of(1L))).isTrue();
        assertThat(coupon.isLineEligible("p2", Set.of(1L))).isFalse();
    }

    @Test
    void isLineEligible_categoryScope_acceptsSharedCategoryLines() {
        Coupon coupon = Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null,
                CouponScope.CATEGORY, Set.of(), Set.of(3L, 7L), null);

        assertThat(coupon.isLineEligible("p1", Set.of(1L, 7L))).isTrue();
        assertThat(coupon.isLineEligible("p2", Set.of(1L))).isFalse();
        assertThat(coupon.isLineEligible("p3", Set.of())).isFalse();
        assertThat(coupon.isLineEligible("p4", null)).isFalse();
    }

    @Test
    void canBeUsedByUser_respectsPerUserCap() {
        Coupon coupon = Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null,
                CouponScope.ALL, Set.of(), Set.of(), 2);

        assertThat(coupon.canBeUsedByUser(0)).isTrue();
        assertThat(coupon.canBeUsedByUser(1)).isTrue();
        assertThat(coupon.canBeUsedByUser(2)).isFalse();
    }

    @Test
    void canBeUsedByUser_noCap_isAlwaysTrue() {
        Coupon coupon = Coupon.create("SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null);

        assertThat(coupon.canBeUsedByUser(10)).isTrue();
    }

    @Test
    void reconstitute_defaultsMissingScopeToAll() {
        Coupon coupon = Coupon.reconstitute("c-1", "SAVE", CouponType.FIXED,
                new BigDecimal("5"), true, null, null, null, null,
                Set.of(), Set.of(), null, 0, Instant.now());

        assertThat(coupon.getScope()).isEqualTo(CouponScope.ALL);
    }
}
