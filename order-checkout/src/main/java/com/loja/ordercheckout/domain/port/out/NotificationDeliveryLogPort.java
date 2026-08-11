package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;

/**
 * Outbound port for the notification delivery log. Provides audit + idempotency
 * for transactional notification emails: exactly one row per business event, keyed
 * by a unique idempotency key.
 */
public interface NotificationDeliveryLogPort {

    /**
     * Claims the delivery by inserting a PENDING row if the idempotency key is
     * absent. Implementations must be safe against a concurrent claim of the same
     * key (unique constraint).
     *
     * @param delivery the new delivery to claim
     * @return {@code true} if the row was inserted, {@code false} if the key already exists
     */
    boolean claim(NotificationDelivery delivery);

    /**
     * Moves an existing delivery to the given terminal status (SENT or FAILED),
     * recording an error message and bumping the attempt count on failure. No-op
     * when the key is unknown.
     */
    void updateStatus(String idempotencyKey, NotificationDeliveryStatus status, String errorMessage);
}