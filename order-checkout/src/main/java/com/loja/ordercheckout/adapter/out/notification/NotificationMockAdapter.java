package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.port.out.NotificationPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * FOR LOCAL DEV ONLY.
 *
 * Mock notification channel that logs every notification to the application log and
 * records it in an in-memory list (for test verification) instead of sending real
 * emails or SMS. Every entry includes the customer email and the order id; shipped
 * notifications also carry the carrier name and tracking number.
 */
@ApplicationScoped
public class NotificationMockAdapter implements NotificationPort {

    public static final String CARRIER = "Correios";

    private static final Logger LOGGER = Logger.getLogger(NotificationMockAdapter.class.getName());

    private final List<String> notifications = Collections.synchronizedList(new ArrayList<>());

    /** Unmodifiable snapshot of the recorded notification entries (for tests and dev debugging). */
    public List<String> getNotifications() {
        synchronized (notifications) {
            return List.copyOf(notifications);
        }
    }

    @Override
    public void notifyOrderConfirmed(Order order) {
        record("ORDER_CONFIRMED", order, "total=" + order.getTotal());
    }

    @Override
    public void notifyOrderShipped(Order order, String trackingNumber) {
        record("ORDER_SHIPPED", order, "carrier=" + CARRIER + " tracking=" + trackingNumber);
    }

    @Override
    public void notifyRefundRequested(Order order, String reason) {
        record("REFUND_REQUESTED", order, "reason=" + reason);
    }

    @Override
    public void notifyRefundApproved(Order order, RefundRequest request) {
        record("REFUND_APPROVED", order, "refund=" + request.getId() + " amount=" + request.getAmount()
                + " reason=" + request.getReason());
    }

    @Override
    public void notifyRefundRejected(Order order, RefundRequest request) {
        record("REFUND_REJECTED", order, "refund=" + request.getId() + " reason=" + request.getRejectionReason());
    }

    private void record(String type, Order order, String details) {
        String entry = type + " order=" + order.getId() + " email=" + order.getCustomerEmail()
                + " " + details;
        LOGGER.info(entry);
        notifications.add(entry);
    }
}
