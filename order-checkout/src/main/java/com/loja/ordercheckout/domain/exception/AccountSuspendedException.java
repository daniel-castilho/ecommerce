package com.loja.ordercheckout.domain.exception;

/**
 * Thrown when a customer whose account is suspended or blocked tries to place
 * an order. Blocked accounts are represented as {@code UserStatus.INACTIVE}.
 */
public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException(String message) {
        super(message);
    }
}
