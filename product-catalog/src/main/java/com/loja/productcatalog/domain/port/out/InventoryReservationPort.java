package com.loja.productcatalog.domain.port.out;

import com.loja.productcatalog.application.dto.ReservationRequest;
import java.util.List;

/**
 * Outbound port for inventory reservation: holds units of a product so a
 * checkout can proceed without the stock being sold to someone else. A
 * reservation reduces the available stock immediately and records a hold row
 * with an expiry time; the caller either {@link #confirm(String) confirms} it
 * once the order is paid (the units stay decremented) or it is
 * {@link #release(String) released} when the order fails or is cancelled.
 * Expired holds are released lazily on the next operation touching the same
 * product, and a scheduled sweep ({@link #expireExpired()}) frees every
 * abandoned hold once its TTL passes, so stock does not stay locked after a
 * checkout that is never finished.
 *
 * <p>All operations are expected to run inside the caller's transaction: a
 * reserve either succeeds for every requested line or rolls back entirely.
 */
public interface InventoryReservationPort {

    /** How long an unconfirmed reservation holds stock before being released. */
    int DEFAULT_TTL_MINUTES = 30;

    /**
     * Atomically reserves {@code quantity} units of each requested product.
     * Releases any expired holds for those products first. Replaying the same
     * {@code reservationId} is a no-op (idempotent).
     *
     * @param reservationId caller-generated id (e.g. the order id)
     * @param items products and quantities to reserve
     * @throws com.loja.productcatalog.domain.exception.InsufficientStockException
     *         if any line cannot be fully reserved; nothing is changed in that case
     */
    void reserve(String reservationId, List<ReservationRequest> items);

    /**
     * Finalizes a reservation after a successful payment. The reserved units are
     * already excluded from available stock, so this only removes the hold row;
     * the decrement becomes permanent.
     *
     * @param reservationId id passed to {@link #reserve(String, List)}
     */
    void confirm(String reservationId);

    /**
     * Returns the reserved units to available stock and removes the hold rows.
     * No-op when the reservation does not exist (e.g. an order created before
     * reservations were introduced).
     *
     * @param reservationId id passed to {@link #reserve(String, List)}
     */
    void release(String reservationId);

    /**
     * Releases every hold whose expiry time has passed, returning the units to
     * available stock and removing the hold rows. Idempotent: holds already
     * released (or confirmed) are not matched again. Returns how many holds
     * were released (0 when there is nothing expired).
     */
    int expireExpired();
}
