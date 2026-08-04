package com.loja.shared.event;

import java.time.Instant;

/** Cross-module domain event: an approved refund was reversed at the payment provider and marked PROCESSED. */
public record RefundProcessedEvent(String refundId, String orderId, Instant occurredAt) implements DomainEvent {
}
