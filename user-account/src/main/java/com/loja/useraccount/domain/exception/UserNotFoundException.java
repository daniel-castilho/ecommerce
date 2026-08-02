package com.loja.useraccount.domain.exception;

/**
 * Exception thrown when a requested user does not exist.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
