package com.loja.promotions.adapter.out.persistence;

import com.loja.promotions.domain.model.Coupon;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
        entity.setScope(coupon.getScope());
        entity.setProductIds(toCsv(coupon.getProductIds()));
        entity.setCategoryIds(toCsv(coupon.getCategoryIds().stream().map(String::valueOf).collect(Collectors.toSet())));
        entity.setMaxUsesPerUser(coupon.getMaxUsesPerUser());
        entity.setUsedCount(coupon.getUsedCount());
        entity.setCreatedAt(coupon.getCreatedAt());
        return entity;
    }

    static Coupon toDomain(CouponJpaEntity entity) {
        return Coupon.reconstitute(entity.getId(), entity.getCode(), entity.getType(),
                entity.getValue(), entity.isActive(), entity.getValidFrom(), entity.getValidTo(),
                entity.getMaxTotalUses(), entity.getScope(),
                fromCsv(entity.getProductIds()),
                fromCsv(entity.getCategoryIds()).stream().map(Long::parseLong).collect(Collectors.toSet()),
                entity.getMaxUsesPerUser(), entity.getUsedCount(), entity.getCreatedAt());
    }

    private static String toCsv(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private static Set<String> fromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
