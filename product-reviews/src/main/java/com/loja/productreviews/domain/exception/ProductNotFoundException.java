package com.loja.productreviews.domain.exception;

/**
 * Thrown when a product id referenced from a review use case cannot be
 * resolved by {@code ProductLookupPort}. Re-exported in this module so
 * the application layer does not have to import product-catalog exceptions.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Product not found: " + productId);
    }
}
