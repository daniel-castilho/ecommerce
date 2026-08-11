package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import java.util.List;

/**
 * Outbound port for the notification delivery log (which doubles as the transactional
 * outbox since Phase C). Provides audit + idempotency for transactional notification
 * emails: exactly one row per business event, keyed by a unique idempotency key.
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
     * Moves an existing delivery to SENT or FAILED. On SENT the error is cleared and
     * the next-attempt gate is dropped. On FAILED the attempt count is bumped: while it
     * stays below {@link NotificationDelivery#MAX_ATTEMPTS} the status becomes FAILED and
     * the next dispatch is gated behind the backoff deadline; on the final failure the
     * status escalates to EXHAUSTED (never polled again). No-op when the key is unknown.
     */
    void updateStatus(String idempotencyKey, NotificationDeliveryStatus status, String errorMessage);

    /**
     * Returns the dispatch queue: deliveries still owed to the channel, oldest
     * first — PENDING rows plus FAILED rows below the retry cap, whose backoff
     * deadline has passed. Rows carrying no rendered body snapshot are excluded,
     * as are EXHAUSTED/SENT rows.
     *
     * @param limit the maximum number of rows to return
     * @return due deliveries ordered by {@code next_attempt_at} then {@code created_at}
     */
    List<NotificationDelivery> findDue(int limit);

    /**
     * Lists deliveries (admin delivery log). Returns all rows when {@code status} is
     * {@code null}, otherwise only rows in that state, newest first.
     */
    List<NotificationDelivery> findDeliveries(NotificationDeliveryStatus status);

    /**
     * Manually re-queues a delivery for dispatch: resets it to PENDING with zero
     * attempts, the next-attempt gate opened and the last error cleared.
     *
     * @param idempotencyKey the delivery to resend
     * @return {@code true} if the delivery existed and was re-queued, {@code false} otherwise
     */
    boolean resend(String idempotencyKey);
}
