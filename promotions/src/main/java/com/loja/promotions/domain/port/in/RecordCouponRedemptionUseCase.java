package com.loja.promotions.domain.port.in;

/**
 * Checkout bridge: records one redemption for a code for a given user
 * (increments the usage counter and writes the per-user redemption ledger).
 * Must be invoked only after the order was successfully persisted. Throws
 * {@code CouponNotFoundException} for unknown codes and
 * {@code CouponNotApplicableException} for exhausted coupons.
 */
public interface RecordCouponRedemptionUseCase {
    void redeem(String code, String userId);
}
