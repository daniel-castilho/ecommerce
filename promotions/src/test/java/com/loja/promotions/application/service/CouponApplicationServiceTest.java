package com.loja.promotions.application.service;

import com.loja.promotions.application.dto.CouponCommand;
import com.loja.promotions.application.dto.DiscountLine;
import com.loja.promotions.application.dto.DiscountQuote;
import com.loja.promotions.application.dto.PageResult;
import com.loja.promotions.domain.exception.CouponNotApplicableException;
import com.loja.promotions.domain.exception.CouponNotFoundException;
import com.loja.promotions.domain.exception.DuplicateCouponCodeException;
import com.loja.promotions.domain.model.Coupon;
import com.loja.promotions.domain.model.CouponScope;
import com.loja.promotions.domain.model.CouponType;
import com.loja.promotions.domain.port.out.CouponRepositoryPort;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponApplicationServiceTest {

    private CouponRepositoryPort repository;
    private CouponApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(CouponRepositoryPort.class);
        service = new CouponApplicationService(repository);
    }

    @Test
    void createCoupon_withNewCode_savesAndReturnsCoupon() {
        Coupon saved = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null);
        when(repository.findByCode("SAVE10")).thenReturn(Optional.empty());
        when(repository.save(any(Coupon.class))).thenReturn(saved);

        Coupon result = service.createCoupon(new CouponCommand("save10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null));

        assertThat(result.getCode()).isEqualTo("SAVE10");
        verify(repository).save(any(Coupon.class));
    }

    @Test
    void createCoupon_withDuplicateCode_throws() {
        when(repository.findByCode("SAVE10")).thenReturn(Optional.of(
                Coupon.create("SAVE10", CouponType.PERCENT, new BigDecimal("10"), true, null, null, null)));

        assertThatThrownBy(() -> service.createCoupon(new CouponCommand("save10",
                CouponType.PERCENT, new BigDecimal("10"), true, null, null, null)))
                .isInstanceOf(DuplicateCouponCodeException.class)
                .hasMessageContaining("SAVE10");
    }

    @Test
    void createCoupon_withCategoryScope_savesScopedCoupon() {
        Coupon saved = Coupon.create("CAT10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null,
                CouponScope.CATEGORY, Set.of(), Set.of(3L, 7L), 2);
        when(repository.findByCode("CAT10")).thenReturn(Optional.empty());
        when(repository.save(any(Coupon.class))).thenReturn(saved);

        Coupon result = service.createCoupon(new CouponCommand("CAT10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null,
                CouponScope.CATEGORY, Set.of(), Set.of(3L, 7L), 2));

        assertThat(result.getScope()).isEqualTo(CouponScope.CATEGORY);
        assertThat(result.getCategoryIds()).containsExactlyInAnyOrder(3L, 7L);
        assertThat(result.getMaxUsesPerUser()).isEqualTo(2);
    }

    @Test
    void listCoupons_delegatesToRepository() {
        PageResult<Coupon> expected = new PageResult<>(List.of(), 0L, 0, 20);
        when(repository.search("SAVE", true, 0, 20)).thenReturn(expected);

        PageResult<Coupon> result = service.listCoupons("SAVE", true, 0, 20);

        assertThat(result).isSameAs(expected);
        verify(repository).search("SAVE", true, 0, 20);
    }

    @Test
    void setActive_deactivatesAndSaves() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null);
        when(repository.findById("c-1")).thenReturn(Optional.of(coupon));

        service.setActive("c-1", false);

        assertThat(coupon.isActive()).isFalse();
        verify(repository).save(coupon);
    }

    @Test
    void setActive_unknownId_throws() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive("missing", true))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void quote_validCoupon_returnsDiscountWithoutIncrementing() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null);
        when(repository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        DiscountQuote quote = service.quote("save10", List.of(
                new DiscountLine("p1", Set.of(1L, 2L), new Money(new BigDecimal("100.00")))));

        assertThat(quote.code()).isEqualTo("SAVE10");
        assertThat(quote.discountAmount().getAmount()).isEqualByComparingTo("10.00");
        assertThat(coupon.getUsedCount()).isZero();
    }

    @Test
    void quote_unknownCode_throws() {
        when(repository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quote("nope", List.of(
                new DiscountLine("p1", Set.of(1L), new Money(new BigDecimal("100.00"))))))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void quote_inactiveCoupon_throws() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), false, null, null, null);
        when(repository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.quote("SAVE10", List.of(
                new DiscountLine("p1", Set.of(1L), new Money(new BigDecimal("100.00"))))))
                .isInstanceOf(CouponNotApplicableException.class);
    }

    @Test
    void quote_categoryScope_discountsOnlyEligibleLines() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null,
                CouponScope.CATEGORY, Set.of(), Set.of(3L), null);
        when(repository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        DiscountQuote quote = service.quote("SAVE10", List.of(
                new DiscountLine("p1", Set.of(3L), new Money(new BigDecimal("100.00"))),
                new DiscountLine("p2", Set.of(9L), new Money(new BigDecimal("100.00")))));

        assertThat(quote.discountAmount().getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void quote_categoryScope_noEligibleLines_returnsZero() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, null,
                CouponScope.CATEGORY, Set.of(), Set.of(3L), null);
        when(repository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        DiscountQuote quote = service.quote("SAVE10", List.of(
                new DiscountLine("p2", Set.of(9L), new Money(new BigDecimal("100.00")))));

        assertThat(quote.discountAmount().getAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void redeem_incrementsUsageAndSaves() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, 5);
        when(repository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));
        when(repository.countRedemptionsByUser(coupon.getId(), "u-1")).thenReturn(0L);

        service.redeem("save10", "u-1");

        assertThat(coupon.getUsedCount()).isEqualTo(1);
        verify(repository).save(coupon);
        verify(repository).recordRedemption(eq(coupon.getId()), eq("u-1"), any(java.time.Instant.class));
    }

    @Test
    void redeem_exhaustedCoupon_throwsWithoutSaving() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, 1);
        coupon.recordUsage();
        when(repository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.redeem("SAVE10", "u-1"))
                .isInstanceOf(CouponNotApplicableException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void redeem_userOverPerUserCap_throwsWithoutSaving() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, 5, null, null, null, 1);
        when(repository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));
        when(repository.countRedemptionsByUser(coupon.getId(), "u-1")).thenReturn(1L);

        assertThatThrownBy(() -> service.redeem("SAVE10", "u-1"))
                .isInstanceOf(CouponNotApplicableException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
        verify(repository, org.mockito.Mockito.never()).recordRedemption(any(), any(), any());
    }
}
