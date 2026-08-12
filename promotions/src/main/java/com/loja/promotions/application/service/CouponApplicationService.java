package com.loja.promotions.application.service;

import com.loja.promotions.application.dto.CouponCommand;
import com.loja.promotions.application.dto.DiscountLine;
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
import java.util.List;
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
                command.active(), command.validFrom(), command.validTo(), command.maxTotalUses(),
                command.scope(), command.productIds(), command.categoryIds(), command.maxUsesPerUser());
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
    public DiscountQuote quote(String code, List<DiscountLine> lines) {
        Coupon coupon = requireByCode(code);
        if (!coupon.canBeUsed(Instant.now())) {
            throw new CouponNotApplicableException("Coupon " + coupon.getCode() + " is not currently valid");
        }
        Money eligibleSubtotal = lines.stream()
                .filter(line -> coupon.isLineEligible(line.productId(), line.categoryIds()))
                .map(DiscountLine::lineTotal)
                .reduce(Money.zero(), Money::add);
        return new DiscountQuote(coupon.getCode(), coupon.discountFor(eligibleSubtotal));
    }

    @Override
    public void redeem(String code, String userId) {
        Coupon coupon = requireByCodeForUpdate(code);
        if (!coupon.canBeUsed(Instant.now())) {
            throw new CouponNotApplicableException("Coupon " + coupon.getCode() + " is not currently valid");
        }
        if (!coupon.canBeUsedByUser((int) couponRepository.countRedemptionsByUser(coupon.getId(), userId))) {
            throw new CouponNotApplicableException(
                    "Coupon " + coupon.getCode() + " has already been used by this user");
        }
        coupon.recordUsage();
        couponRepository.save(coupon);
        couponRepository.recordRedemption(coupon.getId(), userId, Instant.now());
    }

    private Coupon requireById(String couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + couponId));
    }

    private Coupon requireByCode(String code) {
        return findByNormalized(code).orElseThrow(() -> new CouponNotFoundException("Unknown coupon code: " + code.trim().toUpperCase()));
    }

    /**
     * Same as {@link #requireByCode(String)} but acquires a pessimistic write
     * lock on the row so two concurrent checkouts cannot over-book the usage
     * cap (the increment is read-modify-write on {@code used_count}).
     */
    private Coupon requireByCodeForUpdate(String code) {
        String normalized = requireNonNullCode(code);
        return couponRepository.findByCodeForUpdate(normalized)
                .orElseThrow(() -> new CouponNotFoundException("Unknown coupon code: " + code.trim().toUpperCase()));
    }

    private Optional<Coupon> findByNormalized(String code) {
        return couponRepository.findByCode(requireNonNullCode(code));
    }

    private static String requireNonNullCode(String code) {
        if (code == null || code.isBlank()) {
            throw new CouponNotFoundException("A coupon code is required");
        }
        return code.trim().toUpperCase();
    }
}
