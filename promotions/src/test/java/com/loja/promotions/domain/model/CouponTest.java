package com.loja.promotions.domain.model;

import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

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
}
