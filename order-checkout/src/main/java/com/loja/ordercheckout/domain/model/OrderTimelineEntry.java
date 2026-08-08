package com.loja.ordercheckout.domain.model;

import java.time.Instant;

/**
 * A single entry in an order's status timeline: the status the order was in
 * when the entry was recorded, when it happened, and a human-readable label.
 * Seeded when the order is placed and appended on every status transition.
 */
public record OrderTimelineEntry(OrderStatus status, Instant occurredAt, String label) {
}
