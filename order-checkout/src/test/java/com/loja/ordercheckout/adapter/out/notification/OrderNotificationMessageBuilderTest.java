package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.shared.domain.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNotificationMessageBuilderTest {

    private final Order order = Order.create("user-1", "buyer@example.com",
            List.of(new OrderLine("SKU-001", "QA Test Widget", new Money(new BigDecimal("29.90")), 2, 0)),
            new ShippingAddress("Ana Souza", "Rua das Flores", "123", null, "Centro", "São Paulo", "SP",
                    "01000-000", "11999999999"));

    @Test
    void orderConfirmed_subjectAndBodyIncludeOrderIdItemsAndTotal() {
        OrderNotificationMessageBuilder.Draft draft = OrderNotificationMessageBuilder.orderConfirmed(order);

        assertThat(draft.subject()).isEqualTo("Order " + order.getId() + " confirmed");
        assertThat(draft.body())
                .contains(order.getId())
                .contains("- QA Test Widget x 2 ($59.80)")
                .contains("Total: $59.80");
    }

    @Test
    void orderShipped_bodyIncludesTrackingNumber() {
        OrderNotificationMessageBuilder.Draft draft = OrderNotificationMessageBuilder.orderShipped(order, "AA123BR");

        assertThat(draft.subject()).isEqualTo("Order " + order.getId() + " shipped");
        assertThat(draft.body())
                .contains(order.getId())
                .contains("Tracking number: AA123BR");
    }

    @Test
    void refundRequested_bodyIncludesReason() {
        OrderNotificationMessageBuilder.Draft draft
                = OrderNotificationMessageBuilder.refundRequested(order, "Item was damaged");

        assertThat(draft.subject()).isEqualTo("Refund requested for order " + order.getId());
        assertThat(draft.body())
                .contains(order.getId())
                .contains("Reason: Item was damaged");
    }

    @Test
    void refundApproved_bodyIncludesAmountAndReason() {
        RefundRequest request = RefundRequest.request(order.getId(), new Money(new BigDecimal("59.80")), "damaged");
        request.approve();

        OrderNotificationMessageBuilder.Draft draft = OrderNotificationMessageBuilder.refundApproved(order, request);

        assertThat(draft.subject()).isEqualTo("Refund approved for order " + order.getId());
        assertThat(draft.body())
                .contains(order.getId())
                .contains("Amount: $59.80")
                .contains("Reason: damaged");
    }

    @Test
    void refundRejected_bodyIncludesRejectionReason() {
        RefundRequest request = RefundRequest.request(order.getId(), new Money(new BigDecimal("59.80")), "damaged");
        request.reject("Camera not returned");

        OrderNotificationMessageBuilder.Draft draft = OrderNotificationMessageBuilder.refundRejected(order, request);

        assertThat(draft.subject()).isEqualTo("Refund rejected for order " + order.getId());
        assertThat(draft.body())
                .contains(order.getId())
                .contains("Rejection detail: Camera not returned");
    }
}