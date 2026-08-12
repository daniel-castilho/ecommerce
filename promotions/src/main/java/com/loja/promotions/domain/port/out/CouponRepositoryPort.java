package com.loja.promotions.domain.port.out;

import com.loja.promotions.application.dto.PageResult;
import com.loja.promotions.domain.model.Coupon;
import java.util.Optional;

public interface CouponRepositoryPort {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(String id);
    Optional<Coupon> findByCode(String code);
    Optional<Coupon> findByCodeForUpdate(String code);
    PageResult<Coupon> search(String codeFragment, Boolean active, int page, int pageSize);
}
