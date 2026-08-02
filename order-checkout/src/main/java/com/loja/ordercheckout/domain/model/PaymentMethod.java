package com.loja.ordercheckout.domain.model;

/**
 * Tokenized payment method supplied by the client (e.g. a Stripe Elements token or a
 * PagSeguro card token). Holds the tokenized card reference only — never raw card data.
 */
public record PaymentMethod(String method, String token) {

    public PaymentMethod {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Payment token is required");
        }
    }
}
