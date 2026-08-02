package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when a customer notification cannot be delivered.
 */
public class NotificationException extends RuntimeException {

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
