package com.loja.shared.event;

import java.time.Instant;

/** Cross-module domain event: a refund request was rejected with a reason. */
public record RefundRejectedEvent(String refundId, String orderId, String reason, Instant occurredAt) implements DomainEvent {
}
