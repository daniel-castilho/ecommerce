package com.loja.productreviews.domain.port.out;

/**
 * Thin look-up port against {@code product-catalog}.
 *
 * <p>Implemented by {@code ProductLookupAdapter} in this module;
 * the adapter delegates to the public
 * {@code ProductRepositoryPort.findById(String)} of product-catalog.
 */
public interface ProductLookupPort {

    /**
     * @param productId target product
     * @return true iff a product with this id exists
     */
    boolean existsById(String productId);
}
