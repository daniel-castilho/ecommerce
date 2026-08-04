package com.loja.ordercheckout.adapter.out.notification;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class NotificationMockAdapterTest {

    private NotificationMockAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationMockAdapter();
    }

    private Order order() {
        return new Order("order-1", "user-1", "ana@example.com");
    }

    @Test
    void notifyOrderConfirmed_doesNotThrowAndRecordsEntry() {
        assertThatNoException().isThrownBy(() -> adapter.notifyOrderConfirmed(order()));

        List<String> entries = adapter.getNotifications();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).contains("ORDER_CONFIRMED", "order=order-1",
                "email=ana@example.com", "total=");
    }

    @Test
    void notifyOrderShipped_includesTrackingNumberAndCarrier() {
        adapter.notifyOrderShipped(order(), "AA123456789BR");

        assertThat(adapter.getNotifications()).hasSize(1);
        assertThat(adapter.getNotifications().get(0))
                .contains("ORDER_SHIPPED", "order=order-1", "email=ana@example.com",
                        "carrier=" + NotificationMockAdapter.CARRIER, "tracking=AA123456789BR");
    }

    @Test
    void notifyRefundRequested_includesReason() {
        adapter.notifyRefundRequested(order(), "Received the wrong size");

        assertThat(adapter.getNotifications()).hasSize(1);
        assertThat(adapter.getNotifications().get(0))
                .contains("REFUND_REQUESTED", "order=order-1", "email=ana@example.com",
                        "reason=Received the wrong size");
    }

    @Test
    void notifyRefundApproved_includesRefundIdAmountAndReason() {
        adapter.notifyRefundApproved(order(), refund(RefundStatus.APPROVED, null));

        assertThat(adapter.getNotifications()).hasSize(1);
        assertThat(adapter.getNotifications().get(0))
                .contains("REFUND_APPROVED", "order=order-1", "email=ana@example.com",
                        "refund=r-1", "amount=$ 50.00", "reason=Damaged item");
    }

    @Test
    void notifyRefundRejected_includesRejectionReason() {
        adapter.notifyRefundRejected(order(), refund(RefundStatus.REJECTED, "Policy violation"));

        assertThat(adapter.getNotifications()).hasSize(1);
        assertThat(adapter.getNotifications().get(0))
                .contains("REFUND_REJECTED", "order=order-1", "email=ana@example.com",
                        "refund=r-1", "reason=Policy violation");
    }

    @Test
    void allNotifications_includeEmailAndOrderId() {
        adapter.notifyOrderConfirmed(order());
        adapter.notifyOrderShipped(order(), "AA123456789BR");
        adapter.notifyRefundRequested(order(), "Changed my mind");
        adapter.notifyRefundApproved(order(), refund(RefundStatus.PROCESSED, null));
        adapter.notifyRefundRejected(order(), refund(RefundStatus.REJECTED, "Policy violation"));

        List<String> entries = adapter.getNotifications();
        assertThat(entries).hasSize(5);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry).contains("order=order-1", "email=ana@example.com");
        });
    }

    private static RefundRequest refund(RefundStatus status, String rejectionReason) {
        return RefundRequest.reconstitute("r-1", "order-1", new Money(new BigDecimal("50.00")),
                "Damaged item", status, rejectionReason, Instant.now(), null);
    }
}
