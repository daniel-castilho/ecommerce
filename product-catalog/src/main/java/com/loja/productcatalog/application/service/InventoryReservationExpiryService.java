package com.loja.productcatalog.application.service;

import com.loja.productcatalog.domain.port.out.InventoryReservationPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Transactional boundary for the scheduled inventory-reservation expiry sweep.
 * The scheduler must not reach the JPA adapter directly, so this service wraps
 * {@link InventoryReservationPort#expireExpired()} in a container-managed
 * transaction (a crash mid-sweep rolls back and is retried on the next run).
 */
@ApplicationScoped
public class InventoryReservationExpiryService {

    @Inject
    InventoryReservationPort inventoryReservation;

    /**
     * Releases every expired reservation hold inside one transaction.
     *
     * @return number of holds released (0 when nothing was expired)
     */
    @Transactional
    public int releaseExpired() {
        return inventoryReservation.expireExpired();
    }
}
