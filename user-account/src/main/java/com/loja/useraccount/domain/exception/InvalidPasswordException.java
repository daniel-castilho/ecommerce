package com.loja.useraccount.domain.exception;

/**
 * Exception thrown when an invalid password is supplied.
 */
public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
