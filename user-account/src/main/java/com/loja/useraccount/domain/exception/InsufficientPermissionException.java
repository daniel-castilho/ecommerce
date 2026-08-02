package com.loja.useraccount.domain.exception;

/**
 * Exception thrown when the caller lacks the required permission.
 */
public class InsufficientPermissionException extends RuntimeException {
    public InsufficientPermissionException(String message) {
        super(message);
    }
}
