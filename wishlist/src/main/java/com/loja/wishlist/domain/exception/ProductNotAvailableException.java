package com.loja.wishlist.domain.exception;

/**
 * Thrown when a product cannot be added to the wishlist because it does not
 * exist or is not in {@code ACTIVE} status.
 */
public class ProductNotAvailableException extends RuntimeException {

    public ProductNotAvailableException(String productId) {
        super("Product is not available for wishlist: " + productId);
    }
}
