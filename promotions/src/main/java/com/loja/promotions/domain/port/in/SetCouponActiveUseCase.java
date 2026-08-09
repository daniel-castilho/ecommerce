package com.loja.promotions.domain.port.in;

/** Admin use case: activate or deactivate a coupon. */
public interface SetCouponActiveUseCase {
    void setActive(String couponId, boolean active);
}
