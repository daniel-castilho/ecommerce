package com.loja.promotions.adapter.out.persistence;

import com.loja.promotions.domain.model.Coupon;

/** Mapping between the Coupon domain object and its JPA entity. */
final class CouponJpaMapper {

    private CouponJpaMapper() {}

    static CouponJpaEntity toEntity(Coupon coupon) {
        CouponJpaEntity entity = new CouponJpaEntity();
        entity.setId(coupon.getId());
        entity.setCode(coupon.getCode());
        entity.setType(coupon.getType());
        entity.setValue(coupon.getValue());
        entity.setActive(coupon.isActive());
        entity.setValidFrom(coupon.getValidFrom());
        entity.setValidTo(coupon.getValidTo());
        entity.setMaxTotalUses(coupon.getMaxTotalUses());
        entity.setUsedCount(coupon.getUsedCount());
        entity.setCreatedAt(coupon.getCreatedAt());
        return entity;
    }

    static Coupon toDomain(CouponJpaEntity entity) {
        return Coupon.reconstitute(entity.getId(), entity.getCode(), entity.getType(),
                entity.getValue(), entity.isActive(), entity.getValidFrom(), entity.getValidTo(),
                entity.getMaxTotalUses(), entity.getUsedCount(), entity.getCreatedAt());
    }
}
