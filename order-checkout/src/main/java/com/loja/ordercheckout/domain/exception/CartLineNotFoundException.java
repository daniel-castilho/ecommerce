package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when a quantity update targets a product that is not on the cart.
 */
public class CartLineNotFoundException extends RuntimeException {

    public CartLineNotFoundException(String productId) {
        super("Cart line not found for product: " + productId);
    }
}
