package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;
import java.time.Instant;

/**
 * Result of a successful payment authorization, returned by the payment gateway.
 */
public record PaymentAuthorization(String method, String authorizationId, Money amount,
                                   String gatewayTransactionId, Instant authorizedAt) {

    public PaymentAuthorization {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (authorizationId == null || authorizationId.isBlank()) {
            throw new IllegalArgumentException("Authorization id is required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
    }
}
