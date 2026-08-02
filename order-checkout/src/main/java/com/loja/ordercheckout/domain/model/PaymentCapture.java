package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;
import java.time.Instant;

/**
 * Result of a payment capture, returned by the payment gateway after the
 * authorization is confirmed.
 */
public record PaymentCapture(String authorizationId, String captureId, Money amount,
                             String gatewayTransactionId, Instant capturedAt) {

    public PaymentCapture {
        if (authorizationId == null || authorizationId.isBlank()) {
            throw new IllegalArgumentException("Authorization id is required");
        }
        if (captureId == null || captureId.isBlank()) {
            throw new IllegalArgumentException("Capture id is required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
    }
}
