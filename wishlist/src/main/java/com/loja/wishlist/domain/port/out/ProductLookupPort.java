package com.loja.wishlist.domain.port.out;

import java.util.Optional;

import com.loja.wishlist.application.dto.ProductSnapshot;

/**
 * Thin look-up port against {@code product-catalog}.
 *
 * <p>Implemented by {@code ProductLookupAdapter} in this module; the adapter
 * delegates to the public ports of product-catalog and only returns ACTIVE
 * products with display fields for the wishlist UI.
 */
public interface ProductLookupPort {

    /**
     * @param productId target product
     * @return a display snapshot if the product exists and is ACTIVE; empty otherwise
     */
    Optional<ProductSnapshot> findActiveById(String productId);
}
