package com.loja.wishlist.application.dto;

import java.math.BigDecimal;

/**
 * Display snapshot of a product for wishlist screens.
 *
 * <p>Produced by {@link com.loja.wishlist.domain.port.out.ProductLookupPort}
 * from the product-catalog module. {@code imageUrl} may be {@code null} when
 * the product has no images or the storage port cannot resolve a public URL.
 */
public record ProductSnapshot(
        String productId,
        String name,
        String slug,
        BigDecimal price,
        String imageUrl) {
}
