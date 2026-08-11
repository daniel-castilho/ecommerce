package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.exception.NotificationException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.RefundRequest;
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
 * server is down. Respects {@code UserProfile.notificationsEnabled} when the user
 * can be resolved (missing user or failed lookup defaults to sending).
 */
@ApplicationScoped
public class OrderNotificationEmailAdapter implements NotificationPort {

    private static final Logger LOG = Logger.getLogger(OrderNotificationEmailAdapter.class.getName());
    private static final String FROM = "noreply@loja.com";

    @Resource(name = "java:app/env/mail/Session")
    private Session mailSession;

    @Inject
    private FindUserUseCase findUserUseCase;

    protected OrderNotificationEmailAdapter() {
    }

    OrderNotificationEmailAdapter(Session mailSession, FindUserUseCase findUserUseCase) {
        this.mailSession = mailSession;
        this.findUserUseCase = findUserUseCase;
    }

    @Override
    public void notifyOrderConfirmed(Order order) {
        send(order, OrderNotificationMessageBuilder.orderConfirmed(order), "order-confirmed");
    }

    @Override
    public void notifyOrderShipped(Order order, String trackingNumber) throws NotificationException {
        send(order, OrderNotificationMessageBuilder.orderShipped(order, trackingNumber), "order-shipped");
    }

    @Override
    public void notifyRefundRequested(Order order, String reason) {
        send(order, OrderNotificationMessageBuilder.refundRequested(order, reason), "refund-requested");
    }

    @Override
    public void notifyRefundApproved(Order order, RefundRequest request) {
        send(order, OrderNotificationMessageBuilder.refundApproved(order, request), "refund-approved");
    }

    @Override
    public void notifyRefundRejected(Order order, RefundRequest request) {
        send(order, OrderNotificationMessageBuilder.refundRejected(order, request), "refund-rejected");
    }

    private void send(Order order, OrderNotificationMessageBuilder.Draft draft, String event) {
        if (!emailsEnabled(order.getUserId())) {
            LOG.fine(() -> "Skipping " + event + " email for order " + order.getId()
                    + ": notifications disabled");
            return;
        }
        try {
            MimeMessage msg = new MimeMessage(mailSession);
            msg.setFrom(new InternetAddress(FROM));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(order.getCustomerEmail()));
            msg.setSubject(draft.subject());
            msg.setText(draft.body());
            Transport.send(msg);
            LOG.info("Sent " + event + " email to " + order.getCustomerEmail() + " for order " + order.getId());
        } catch (MessagingException | RuntimeException e) {
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