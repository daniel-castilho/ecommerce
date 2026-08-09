package com.loja.promotions.domain.port.in;

import com.loja.promotions.application.dto.PageResult;
import com.loja.promotions.domain.model.Coupon;

/** Admin use case: list coupons, optionally filtered by code fragment and active flag. */
public interface ListCouponsUseCase {
    PageResult<Coupon> listCoupons(String codeFragment, Boolean active, int page, int pageSize);
}
