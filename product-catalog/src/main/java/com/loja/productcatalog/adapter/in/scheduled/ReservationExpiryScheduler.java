package com.loja.productcatalog.adapter.in.scheduled;

import com.loja.productcatalog.application.service.InventoryReservationExpiryService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application-scoped background sweep that releases inventory reservations whose
 * TTL has passed. Runs {@link InventoryReservationExpiryService#releaseExpired()}
 * every {@link #SWEEP_INTERVAL_SECONDS} seconds on a single daemon thread
 * ({@code scheduleWithFixedDelay}, so runs never overlap). Started lazily by the
 * container when the CDI bean is created and shut down on application stop.
 */
@ApplicationScoped
public class ReservationExpiryScheduler {

    public static final int SWEEP_INTERVAL_SECONDS = 60;

    private static final Logger LOGGER = Logger.getLogger(ReservationExpiryScheduler.class.getName());

    @Inject
    private InventoryReservationExpiryService expiryService;

    private ScheduledExecutorService executor;

    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reservation-expiry-sweep");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::sweep, SWEEP_INTERVAL_SECONDS,
                SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        LOGGER.info("Inventory reservation expiry sweep scheduled every "
                + SWEEP_INTERVAL_SECONDS + "s");
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void sweep() {
        try {
            int released = expiryService.releaseExpired();
            if (released > 0) {
                LOGGER.info("Released " + released + " expired inventory reservation hold(s)");
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Inventory reservation expiry sweep failed", e);
        }
    }
}
