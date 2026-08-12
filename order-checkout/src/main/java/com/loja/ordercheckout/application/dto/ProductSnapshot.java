package com.loja.ordercheckout.application.dto;

import com.loja.shared.domain.Money;
import java.util.Set;

/**
 * Display snapshot of a product for cart screens.
 *
 * <p>Produced by {@link com.loja.ordercheckout.domain.port.out.ProductLookupPort}
 * from the product-catalog module. {@code imageUrl} may be {@code null} when the
 * product has no images or the storage port cannot resolve a public URL.
 * {@code categoryIds} feeds the coupon preview so CATEGORY-scoped coupons can be
 * quoted against the real cart lines.
 */
public record ProductSnapshot(
        String productId,
        String name,
        String slug,
        Money price,
        String imageUrl,
        Set<Long> categoryIds) {
}
