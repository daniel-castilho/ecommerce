package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;
import java.time.Instant;

/**
 * Result of a payment refund, returned by the payment gateway.
 */
public record PaymentRefund(String captureId, String refundId, Money amount,
                            String gatewayTransactionId, Instant refundedAt) {

    public PaymentRefund {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
    }
}
