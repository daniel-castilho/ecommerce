package com.loja.ordercheckout.application.dto;

import com.loja.shared.domain.Money;

/**
 * Display snapshot of a product for cart screens.
 *
 * <p>Produced by {@link com.loja.ordercheckout.domain.port.out.ProductLookupPort}
 * from the product-catalog module. {@code imageUrl} may be {@code null} when the
 * product has no images or the storage port cannot resolve a public URL.
 */
public record ProductSnapshot(
        String productId,
        String name,
        String slug,
        Money price,
        String imageUrl) {
}
