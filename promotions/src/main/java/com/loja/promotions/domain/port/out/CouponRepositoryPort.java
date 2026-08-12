package com.loja.promotions.domain.port.out;

import com.loja.promotions.application.dto.PageResult;
import com.loja.promotions.domain.model.Coupon;
import java.time.Instant;
import java.util.Optional;

public interface CouponRepositoryPort {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(String id);
    Optional<Coupon> findByCode(String code);
    Optional<Coupon> findByCodeForUpdate(String code);
    PageResult<Coupon> search(String codeFragment, Boolean active, int page, int pageSize);

    /** Number of times the given user already redeemed the coupon. */
    long countRedemptionsByUser(String couponId, String userId);

    /** Append one row to the per-user redemption ledger. */
    void recordRedemption(String couponId, String userId, Instant redeemedAt);
}
