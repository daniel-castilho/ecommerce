package com.loja.shared.domain;

/**
 * Base exception for business rule violations (not infrastructure errors).
 * Each module should extend this class for its domain-specific exceptions.
 */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
