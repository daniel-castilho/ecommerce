package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when an optimistic-lock conflict is detected on a cart: it was changed
 * by another transaction between read and write. The UI translates it into a
 * "Cart was updated, please reload and try again" message.
 */
public class CartConcurrentModificationException extends RuntimeException {

    public CartConcurrentModificationException(String cartId) {
        super("Cart was modified concurrently: " + cartId);
    }
}
