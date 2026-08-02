package com.loja.ordercheckout.domain.model;

import java.time.Instant;

/**
 * Result of a shipping label creation, returned by the carrier adapter. The
 * tracking number format is carrier-specific (Correios: {@code AA########BR}).
 */
public record ShippingLabel(String trackingNumber, String carrier, String method, Instant createdAt) {

    public ShippingLabel {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number is required");
        }
        if (carrier == null || carrier.isBlank()) {
            throw new IllegalArgumentException("Carrier is required");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Shipping method is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at is required");
        }
    }
}
