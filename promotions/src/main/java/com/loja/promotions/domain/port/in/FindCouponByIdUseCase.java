package com.loja.promotions.domain.port.in;

import com.loja.promotions.domain.model.Coupon;
import java.util.Optional;

/** Admin use case: load a single coupon by id (detail / edit view). */
public interface FindCouponByIdUseCase {
    Optional<Coupon> findById(String couponId);
}
