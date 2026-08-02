package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when a shipping operation (quote or label creation) fails.
 */
public class ShippingException extends RuntimeException {

    public ShippingException(String message) {
        super(message);
    }

    public ShippingException(String message, Throwable cause) {
        super(message, cause);
    }
}
