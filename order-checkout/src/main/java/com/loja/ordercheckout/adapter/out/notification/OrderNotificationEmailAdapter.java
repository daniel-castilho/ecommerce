package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.exception.NotificationException;
import com.loja.ordercheckout.domain.model.NotificationChannel;
import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import com.loja.ordercheckout.domain.port.out.NotificationPort;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Implementation of {@link NotificationPort} that enqueues a transactional outbox
 * entry instead of sending synchronously. Each event is claimed with an idempotency
 * key ({@code EVENT:{orderId}}) and the rendered email payload (recipient, subject,
 * body) is snapshotted in the same business transaction; {@link NotificationOutboxProcessor}
 * dispatches the emails on a schedule. No network I/O happens on the request thread,
 * so checkout never touches SMTP latency and always commits.
 *
 * <p>Respects {@code UserProfile.notificationsEnabled} when the user can be resolved
 * (missing user or failed lookup defaults to enqueuing).
 */
@ApplicationScoped
public class OrderNotificationEmailAdapter implements NotificationPort {

    private static final Logger LOG = Logger.getLogger(OrderNotificationEmailAdapter.class.getName());
    private static final NotificationChannel CHANNEL = NotificationChannel.EMAIL;

    @Inject
    private FindUserUseCase findUserUseCase;

    @Inject
    private NotificationDeliveryLogPort deliveryLog;

    protected OrderNotificationEmailAdapter() {
    }

    OrderNotificationEmailAdapter(FindUserUseCase findUserUseCase,
                                  NotificationDeliveryLogPort deliveryLog) {
        this.findUserUseCase = findUserUseCase;
        this.deliveryLog = deliveryLog;
    }

    @Override
    public void notifyOrderConfirmed(Order order) {
        enqueue(order, OrderNotificationMessageBuilder.orderConfirmed(order), "ORDER_CONFIRMED");
    }

    @Override
    public void notifyOrderShipped(Order order, String trackingNumber) throws NotificationException {
        enqueue(order, OrderNotificationMessageBuilder.orderShipped(order, trackingNumber), "ORDER_SHIPPED");
    }

    @Override
    public void notifyRefundRequested(Order order, String reason) {
        enqueue(order, OrderNotificationMessageBuilder.refundRequested(order, reason), "REFUND_REQUESTED");
    }

    @Override
    public void notifyRefundApproved(Order order, RefundRequest request) {
        enqueue(order, OrderNotificationMessageBuilder.refundApproved(order, request), "REFUND_APPROVED");
    }

    @Override
    public void notifyRefundRejected(Order order, RefundRequest request) {
        enqueue(order, OrderNotificationMessageBuilder.refundRejected(order, request), "REFUND_REJECTED");
    }

    private void enqueue(Order order, OrderNotificationMessageBuilder.Draft draft, String event) {
        if (!emailsEnabled(order.getUserId())) {
            LOG.fine(() -> "Skipping " + event + " email for order " + order.getId()
                    + ": notifications disabled");
            return;
        }
        String idempotencyKey = event + ":" + order.getId();
        NotificationDelivery delivery = NotificationDelivery.create(idempotencyKey, event,
                order.getId(), CHANNEL, order.getCustomerEmail(), draft.subject(), draft.body(),
                draft.htmlBody());
        if (deliveryLog.claim(delivery)) {
            LOG.info("Enqueued " + event + " email for order " + order.getId());
        } else {
            LOG.fine(() -> "Skipping duplicate " + event + " notification for order " + order.getId()
                    + " (already enqueued)");
        }
    }

    private boolean emailsEnabled(String userId) {
        if (userId == null) {
            return true;
        }
        try {
            return findUserUseCase.findById(userId)
                    .map(user -> user.getProfile() != null && user.getProfile().isNotificationsEnabled())
                    .orElse(true);
        } catch (RuntimeException e) {
            LOG.log(java.util.logging.Level.FINE, "Notification preference lookup failed for user " + userId
                    + "; defaulting to enqueue", e);
            return true;
        }
    }
}