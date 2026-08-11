package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.shared.domain.Money;

import java.util.stream.Collectors;

/** Builds plain-text email drafts (subject + body) for order notifications. */
final class OrderNotificationMessageBuilder {

    /** Immutable subject/body pair ready for Jakarta Mail. */
    record Draft(String subject, String body) {}

    private static final String SIGNATURE = "\n\nBest regards,\nThe Loja Team";

    private OrderNotificationMessageBuilder() {
    }

    static Draft orderConfirmed(Order order) {
        return new Draft(
                "Order " + order.getId() + " confirmed",
                "Hi,\n\nYour order " + order.getId() + " has been confirmed.\n\n"
                        + items(order)
                        + "\n\nTotal: " + format(order.getTotal()) + SIGNATURE);
    }

    static Draft orderShipped(Order order, String trackingNumber) {
        return new Draft(
                "Order " + order.getId() + " shipped",
                "Hi,\n\nYour order " + order.getId() + " is on its way.\n\n"
                        + items(order)
                        + "\n\nTracking number: " + trackingNumber + SIGNATURE);
    }

    static Draft refundRequested(Order order, String reason) {
        return new Draft(
                "Refund requested for order " + order.getId(),
                "Hi,\n\nA refund has been requested for order " + order.getId() + ".\n\n"
                        + "Reason: " + reason
                        + "\n\nWe will review it shortly." + SIGNATURE);
    }

    static Draft refundApproved(Order order, RefundRequest request) {
        return new Draft(
                "Refund approved for order " + order.getId(),
                "Hi,\n\nYour refund for order " + order.getId() + " has been approved.\n\n"
                        + "Amount: " + format(request.getAmount())
                        + "\nReason: " + request.getReason() + SIGNATURE);
    }

    static Draft refundRejected(Order order, RefundRequest request) {
        return new Draft(
                "Refund rejected for order " + order.getId(),
                "Hi,\n\nYour refund for order " + order.getId() + " could not be approved.\n\n"
                        + "Reason: " + request.getReason()
                        + "\n\nRejection detail: " + request.getRejectionReason() + SIGNATURE);
    }

    private static String items(Order order) {
        return order.getItems().stream()
                .map(OrderNotificationMessageBuilder::item)
                .collect(Collectors.joining("\n"));
    }

    private static String item(OrderLine line) {
        return "- " + line.getProductName() + " x " + line.getQuantity()
                + " (" + format(line.getLineTotal()) + ")";
    }

    private static String format(Money money) {
        return "$" + money.getAmount();
    }
}
