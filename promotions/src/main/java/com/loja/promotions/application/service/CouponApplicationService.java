package com.loja.promotions.application.service;

import com.loja.promotions.application.dto.CouponCommand;
import com.loja.promotions.application.dto.DiscountQuote;
import com.loja.promotions.application.dto.PageResult;
import com.loja.promotions.domain.exception.CouponNotApplicableException;
import com.loja.promotions.domain.exception.CouponNotFoundException;
import com.loja.promotions.domain.exception.DuplicateCouponCodeException;
import com.loja.promotions.domain.model.Coupon;
import com.loja.promotions.domain.port.in.CreateCouponUseCase;
import com.loja.promotions.domain.port.in.FindCouponByIdUseCase;
import com.loja.promotions.domain.port.in.ListCouponsUseCase;
import com.loja.promotions.domain.port.in.QuoteDiscountUseCase;
import com.loja.promotions.domain.port.in.RecordCouponRedemptionUseCase;
import com.loja.promotions.domain.port.in.SetCouponActiveUseCase;
import com.loja.promotions.domain.port.out.CouponRepositoryPort;
import com.loja.shared.domain.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class CouponApplicationService implements CreateCouponUseCase, ListCouponsUseCase,
        SetCouponActiveUseCase, FindCouponByIdUseCase, QuoteDiscountUseCase,
        RecordCouponRedemptionUseCase {

    private final CouponRepositoryPort couponRepository;

    @Inject
    public CouponApplicationService(CouponRepositoryPort couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Override
    public Coupon createCoupon(CouponCommand command) {
        String code = command.code() == null ? null : command.code().trim().toUpperCase();
        if (couponRepository.findByCode(code).isPresent()) {
            throw new DuplicateCouponCodeException("A coupon with code " + code + " already exists");
        }
        Coupon coupon = Coupon.create(command.code(), command.type(), command.value(),
                command.active(), command.validFrom(), command.validTo(), command.maxTotalUses());
        return couponRepository.save(coupon);
    }

    @Override
    public PageResult<Coupon> listCoupons(String codeFragment, Boolean active, int page, int pageSize) {
        return couponRepository.search(codeFragment, active, page, pageSize);
    }

    @Override
    public void setActive(String couponId, boolean active) {
        Coupon coupon = requireById(couponId);
        if (active) {
            coupon.activate();
        } else {
            coupon.deactivate();
        }
        couponRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(String couponId) {
        return couponRepository.findById(couponId);
    }

    @Override
    public DiscountQuote quote(String code, Money merchandiseSubtotal) {
        Coupon coupon = requireByCode(code);
        if (!coupon.canBeUsed(Instant.now())) {
            throw new CouponNotApplicableException("Coupon " + coupon.getCode() + " is not currently valid");
        }
        return new DiscountQuote(coupon.getCode(), coupon.discountFor(merchandiseSubtotal));
    }

    @Override
    public void redeem(String code) {
        Coupon coupon = requireByCode(code);
        if (!coupon.canBeUsed(Instant.now())) {
            throw new CouponNotApplicableException("Coupon " + coupon.getCode() + " is not currently valid");
        }
        coupon.recordUsage();
        couponRepository.save(coupon);
    }

    private Coupon requireById(String couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + couponId));
    }

    private Coupon requireByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new CouponNotFoundException("A coupon code is required");
        }
        return couponRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new CouponNotFoundException("Unknown coupon code: " + code.trim().toUpperCase()));
    }
}
