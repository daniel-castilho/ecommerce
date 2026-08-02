package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when an optimistic-lock conflict is detected: the order was changed by
 * another transaction between read and write. The UI translates it into a
 * "please reload and try again" message.
 */
public class OrderConcurrentModificationException extends RuntimeException {

    public OrderConcurrentModificationException(String orderId) {
        super("Order was modified concurrently: " + orderId);
    }
}
