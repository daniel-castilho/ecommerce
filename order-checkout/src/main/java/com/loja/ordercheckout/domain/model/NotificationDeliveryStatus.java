package com.loja.ordercheckout.domain.model;

/** Lifecycle of a single notification delivery attempt. */
public enum NotificationDeliveryStatus {
    /** Claimed but the channel has not been invoked yet. */
    PENDING,
    /** The channel accepted the delivery. */
    SENT,
    /** The channel rejected the delivery; the error is recorded. */
    FAILED,
    /** Attempts exhausted after {@code NotificationDelivery.MAX_ATTEMPTS} tries; never polled again. */
    EXHAUSTED
}
