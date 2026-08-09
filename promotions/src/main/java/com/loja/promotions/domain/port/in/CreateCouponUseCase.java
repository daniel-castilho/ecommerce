package com.loja.promotions.domain.port.in;

import com.loja.promotions.application.dto.CouponCommand;
import com.loja.promotions.domain.model.Coupon;

/** Admin use case: create a coupon. Throws on duplicate codes. */
public interface CreateCouponUseCase {
    Coupon createCoupon(CouponCommand command);
}
