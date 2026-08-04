package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when an order cannot be found by the given id.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
