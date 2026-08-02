package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when an order operation is attempted in a state that does not allow it.
 */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
