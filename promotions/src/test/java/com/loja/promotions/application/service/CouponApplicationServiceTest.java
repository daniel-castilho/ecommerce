package com.loja.promotions.application.service;

import com.loja.promotions.application.dto.CouponCommand;
import com.loja.promotions.application.dto.DiscountQuote;
import com.loja.promotions.application.dto.PageResult;
import com.loja.promotions.domain.exception.CouponNotApplicableException;
import com.loja.promotions.domain.exception.CouponNotFoundException;
import com.loja.promotions.domain.exception.DuplicateCouponCodeException;
import com.loja.promotions.domain.model.Coupon;
import com.loja.promotions.domain.model.CouponType;
import com.loja.promotions.domain.port.out.CouponRepositoryPort;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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

        DiscountQuote quote = service.quote("save10", new Money(new BigDecimal("100.00")));

        assertThat(quote.code()).isEqualTo("SAVE10");
        assertThat(quote.discountAmount().getAmount()).isEqualByComparingTo("10.00");
        assertThat(coupon.getUsedCount()).isZero();
    }

    @Test
    void quote_unknownCode_throws() {
        when(repository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quote("nope", new Money(new BigDecimal("100.00"))))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void quote_inactiveCoupon_throws() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), false, null, null, null);
        when(repository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.quote("SAVE10", new Money(new BigDecimal("100.00"))))
                .isInstanceOf(CouponNotApplicableException.class);
    }

    @Test
    void redeem_incrementsUsageAndSaves() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, 5);
        when(repository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));

        service.redeem("save10");

        assertThat(coupon.getUsedCount()).isEqualTo(1);
        verify(repository).save(coupon);
    }

    @Test
    void redeem_exhaustedCoupon_throwsWithoutSaving() {
        Coupon coupon = Coupon.create("SAVE10", CouponType.PERCENT,
                new BigDecimal("10"), true, null, null, 1);
        coupon.recordUsage();
        when(repository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.redeem("SAVE10"))
                .isInstanceOf(CouponNotApplicableException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
