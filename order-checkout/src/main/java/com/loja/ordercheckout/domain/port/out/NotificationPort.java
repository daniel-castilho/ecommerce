package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.domain.exception.NotificationException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.RefundRequest;

/**
 * Outbound port for customer notifications (email/SMS). Every notification must
 * carry the customer email address and the order id. Implementations wrap a real
 * mail/SMS provider or a local mock.
 */
public interface NotificationPort {

    /**
     * Notifies the customer that their order was confirmed (payment captured).
     *
     * @param order the confirmed order
     * @throws NotificationException if the notification cannot be delivered
     */
    void notifyOrderConfirmed(Order order) throws NotificationException;

    /**
     * Notifies the customer that their order was shipped.
     *
     * @param order          the shipped order
     * @param trackingNumber the carrier tracking number to include in the message
     * @throws NotificationException if the notification cannot be delivered
     */
    void notifyOrderShipped(Order order, String trackingNumber) throws NotificationException;

    /**
     * Notifies the customer that a refund was requested for their order.
     *
     * @param order  the order being refunded
     * @param reason the customer-provided refund reason to include in the message
     * @throws NotificationException if the notification cannot be delivered
     */
    void notifyRefundRequested(Order order, String reason) throws NotificationException;

    /**
     * Notifies the customer that their refund was approved and the charge reversed.
     *
     * @param order   the order being refunded
     * @param request the approved and processed refund request
     * @throws NotificationException if the notification cannot be delivered
     */
    void notifyRefundApproved(Order order, RefundRequest request) throws NotificationException;

    /**
     * Notifies the customer that their refund request was rejected.
     *
     * @param order   the order for which the refund was requested
     * @param request the rejected refund request (carries the rejection reason)
     * @throws NotificationException if the notification cannot be delivered
     */
    void notifyRefundRejected(Order order, RefundRequest request) throws NotificationException;
}
