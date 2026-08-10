package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when a product cannot be added to the cart because it does not exist
 * or is not in {@code ACTIVE} status.
 */
public class CartProductNotAvailableException extends RuntimeException {

    public CartProductNotAvailableException(String productId) {
        super("Product is not available for the cart: " + productId);
    }
}
