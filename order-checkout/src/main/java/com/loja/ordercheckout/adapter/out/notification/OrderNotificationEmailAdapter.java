package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.exception.NotificationException;
import com.loja.ordercheckout.domain.model.NotificationChannel;
import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import com.loja.ordercheckout.domain.port.out.NotificationPort;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Real Jakarta Mail implementation of {@link NotificationPort}. Sends best-effort
 * plain-text emails for order lifecycle events; a delivery failure is logged and
 * never propagates, so the business transaction always commits even when the mail
 * server is down.
 *
 * <p>Every event is first {@link NotificationDeliveryLogPort#claim(NotificationDelivery)
 * claimed} with an idempotency key ({@code EVENT:{orderId}}); a duplicate event is
 * skipped, and the outcome (SENT / FAILED) is recorded in the delivery log. Respects
 * {@code UserProfile.notificationsEnabled} when the user can be resolved (missing user
 * or failed lookup defaults to sending).
 */
@ApplicationScoped
public class OrderNotificationEmailAdapter implements NotificationPort {

    private static final Logger LOG = Logger.getLogger(OrderNotificationEmailAdapter.class.getName());
    private static final String FROM = "noreply@loja.com";
    private static final NotificationChannel CHANNEL = NotificationChannel.EMAIL;

    @Resource(name = "java:app/env/mail/Session")
    private Session mailSession;

    @Inject
    private FindUserUseCase findUserUseCase;

    @Inject
    private NotificationDeliveryLogPort deliveryLog;

    protected OrderNotificationEmailAdapter() {
    }

    OrderNotificationEmailAdapter(Session mailSession, FindUserUseCase findUserUseCase,
                                  NotificationDeliveryLogPort deliveryLog) {
        this.mailSession = mailSession;
        this.findUserUseCase = findUserUseCase;
        this.deliveryLog = deliveryLog;
    }

    @Override
    public void notifyOrderConfirmed(Order order) {
        send(order, OrderNotificationMessageBuilder.orderConfirmed(order), "ORDER_CONFIRMED");
    }

    @Override
    public void notifyOrderShipped(Order order, String trackingNumber) throws NotificationException {
        send(order, OrderNotificationMessageBuilder.orderShipped(order, trackingNumber), "ORDER_SHIPPED");
    }

    @Override
    public void notifyRefundRequested(Order order, String reason) {
        send(order, OrderNotificationMessageBuilder.refundRequested(order, reason), "REFUND_REQUESTED");
    }

    @Override
    public void notifyRefundApproved(Order order, RefundRequest request) {
        send(order, OrderNotificationMessageBuilder.refundApproved(order, request), "REFUND_APPROVED");
    }

    @Override
    public void notifyRefundRejected(Order order, RefundRequest request) {
        send(order, OrderNotificationMessageBuilder.refundRejected(order, request), "REFUND_REJECTED");
    }

    private void send(Order order, OrderNotificationMessageBuilder.Draft draft, String event) {
        if (!emailsEnabled(order.getUserId())) {
            LOG.fine(() -> "Skipping " + event + " email for order " + order.getId()
                    + ": notifications disabled");
            return;
        }
        String idempotencyKey = event + ":" + order.getId();
        NotificationDelivery delivery = NotificationDelivery.create(idempotencyKey, event,
                order.getId(), CHANNEL);
        if (!deliveryLog.claim(delivery)) {
            LOG.fine(() -> "Skipping duplicate " + event + " notification for order " + order.getId()
                    + " (already attempted)");
            return;
        }
        try {
            MimeMessage msg = new MimeMessage(mailSession);
            msg.setFrom(new InternetAddress(FROM));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(order.getCustomerEmail()));
            msg.setSubject(draft.subject());
            msg.setText(draft.body());
            Transport.send(msg);
            deliveryLog.updateStatus(idempotencyKey, NotificationDeliveryStatus.SENT, null);
            LOG.info("Sent " + event + " email to " + order.getCustomerEmail() + " for order " + order.getId());
        } catch (MessagingException | RuntimeException e) {
            deliveryLog.updateStatus(idempotencyKey, NotificationDeliveryStatus.FAILED, e.getMessage());
            LOG.log(Level.WARNING, "Failed to send " + event + " email to " + order.getCustomerEmail()
                    + " for order " + order.getId(), e);
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
            LOG.log(Level.FINE, "Notification preference lookup failed for user " + userId
                    + "; defaulting to send", e);
            return true;
        }
    }
}