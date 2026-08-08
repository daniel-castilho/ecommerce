package com.loja.admindashboard.application.dto;

import com.loja.shared.domain.Money;
import java.time.Instant;

/**
 * Presentation-friendly row of the admin order-detail payment transactions
 * table (authorization, capture, refund request/outcome).
 *
 * @param type      transaction kind, e.g. AUTHORIZATION, CAPTURE, REFUND_PROCESSED
 * @param amount    monetary amount of the transaction
 * @param occurredAt when the transaction happened
 * @param reference gateway transaction id (auth/capture) or refund reason
 */
public record PaymentTransactionDTO(String type, Money amount, Instant occurredAt, String reference) {
}
