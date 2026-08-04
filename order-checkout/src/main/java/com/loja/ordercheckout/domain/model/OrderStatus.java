package com.loja.ordercheckout.domain.model;

/**
 * Lifecycle states of an order. The initial state is PENDING; the happy path is
 * PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED. CANCELLED and REFUNDED
 * are terminal branch states. REFUND_REQUESTED marks an order awaiting a refund
 * decision (→ REFUNDED when approved, back to DELIVERED when rejected). Allowed
 * transitions are enforced by {@link Order#canTransitionTo(OrderStatus)}.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUND_REQUESTED,
    REFUNDED
}
