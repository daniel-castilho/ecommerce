package com.loja.ordercheckout.adapter.out.notification;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Schedules {@link NotificationOutboxProcessor#processPending()} on the default
 * managed scheduled executor (concurrent-3.1 feature; no EJB timer). A fixed-delay
 * schedule never overlaps runs, and a repeat of the poll is always rescheduled even
 * if a batch fails.
 *
 * <p>CDI instantiates normal-scoped beans lazily, so an un-referenced bean would
 * never start. Observing {@code @Initialized(ApplicationScoped.class)} forces the
 * container to create this bean (and inject the executor) at application startup,
 * which is when the poller must begin running.
 */
@ApplicationScoped
public class NotificationOutboxDispatcher {

    private static final Logger LOG = Logger.getLogger(NotificationOutboxDispatcher.class.getName());
    private static final long POLL_INTERVAL_SECONDS = 5;

    @Resource(name = "java:comp/DefaultManagedScheduledExecutorService")
    private ManagedScheduledExecutorService scheduler;

    @Inject
    private NotificationOutboxProcessor processor;

    private volatile ScheduledFuture<?> pollingTask;

    protected NotificationOutboxDispatcher() {
    }

    NotificationOutboxDispatcher(ManagedScheduledExecutorService scheduler,
                                 NotificationOutboxProcessor processor) {
        this.scheduler = scheduler;
        this.processor = processor;
    }

    void schedulePolling(@Observes @Initialized(ApplicationScoped.class) Object event) {
        pollingTask = scheduler.scheduleWithFixedDelay(() -> {
            try {
                processor.processPending();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Notification outbox poll failed", e);
            }
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        LOG.info("Scheduled notification outbox poll every " + POLL_INTERVAL_SECONDS + "s");
    }

    /**
     * Cancels the fixed-delay task on app shutdown so a hot redeploy (or stop) does not
     * leak a zombie poller that keeps failing against a destroyed Weld context.
     */
    void stopPolling(@Observes @Destroyed(ApplicationScoped.class) Object event) {
        if (pollingTask != null) {
            pollingTask.cancel(false);
            LOG.info("Cancelled notification outbox poll");
        }
    }
}