package com.loja.useraccount.domain.exception;

/**
 * Exception thrown when an e-mail address is already registered.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
