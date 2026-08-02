package com.loja.productcatalog.domain.exception;

public class InvalidProductImageException extends RuntimeException {
    public InvalidProductImageException(String message) {
        super(message);
    }
}
