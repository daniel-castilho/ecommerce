package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when a payment gateway operation (authorize, capture, refund) fails.
 */
public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(String message) {
        super(message);
    }

    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
