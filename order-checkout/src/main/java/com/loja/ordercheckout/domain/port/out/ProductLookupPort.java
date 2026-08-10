package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.application.dto.ProductSnapshot;
import java.util.Optional;

/**
 * Thin look-up port against {@code product-catalog}.
 *
 * <p>Implemented by {@code ProductLookupAdapter} in this module; the adapter
 * delegates to the public ports of product-catalog and only returns ACTIVE
 * products with display fields for the cart UI.
 */
public interface ProductLookupPort {

    /**
     * @param productId target product
     * @return a display snapshot if the product exists and is ACTIVE; empty otherwise
     */
    Optional<ProductSnapshot> findActiveById(String productId);
}
