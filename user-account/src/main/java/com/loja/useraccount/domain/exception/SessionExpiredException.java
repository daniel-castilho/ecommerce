package com.loja.useraccount.domain.exception;

/**
 * Exception thrown when the user session has expired.
 */
public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException(String message) {
        super(message);
    }
}
