package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;

/**
 * Shipping option offered by a carrier: delivery method, cost, and estimate.
 * Immutable value object; equality is by value.
 */
public record ShippingOption(String method, Money cost, int estimatedDays, String description) {

    public ShippingOption {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Shipping method is required");
        }
        if (cost == null) {
            throw new IllegalArgumentException("Shipping cost is required");
        }
        if (estimatedDays < 1) {
            throw new IllegalArgumentException("Estimated days must be positive");
        }
    }
}
