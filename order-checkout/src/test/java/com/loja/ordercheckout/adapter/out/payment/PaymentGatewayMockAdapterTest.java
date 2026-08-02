package com.loja.ordercheckout.adapter.out.payment;

import com.loja.ordercheckout.domain.exception.PaymentFailedException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.PaymentAuthorization;
import com.loja.ordercheckout.domain.model.PaymentCapture;
import com.loja.ordercheckout.domain.model.PaymentMethod;
import com.loja.ordercheckout.domain.model.PaymentRefund;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayMockAdapterTest {

    private PaymentGatewayMockAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PaymentGatewayMockAdapter();
    }

    private Order order() {
        Order order = new Order("order-1", "user-1");
        order.addItem(new OrderLine("p1", "Product A", new Money(new BigDecimal("10.00")), 2, 0));
        order.addItem(new OrderLine("p2", "Product B", new Money(new BigDecimal("5.50")), 3, 1));
        return order;
    }

    @Test
    void authorize_returnsAuthorizationWithNonEmptyIdAndOrderTotal() {
        PaymentAuthorization auth = adapter.authorize(order(), new PaymentMethod("card", "tok_test"));

        assertThat(auth.authorizationId()).isNotBlank();
        assertThat(auth.gatewayTransactionId()).isNotBlank();
        assertThat(auth.method()).isEqualTo("card");
        assertThat(auth.amount()).isEqualTo(new Money(new BigDecimal("36.50")));
    }

    @Test
    void capture_returnsCapturedAmountEqualToOrderTotal() {
        Order order = order();
        PaymentAuthorization auth = adapter.authorize(order, new PaymentMethod("card", "tok_test"));

        PaymentCapture capture = adapter.capture(auth.authorizationId());

        assertThat(capture.captureId()).isNotBlank();
        assertThat(capture.authorizationId()).isEqualTo(auth.authorizationId());
        assertThat(capture.amount()).isEqualTo(order.getTotal());
        assertThat(capture.amount()).isEqualTo(new Money(new BigDecimal("36.50")));
    }

    @Test
    void refund_returnsRefundedAmountEqualToRequestedAmount() {
        PaymentAuthorization auth = adapter.authorize(order(), new PaymentMethod("card", "tok_test"));
        PaymentCapture capture = adapter.capture(auth.authorizationId());

        PaymentRefund refund = adapter.refund(capture.captureId(), new Money(new BigDecimal("20.00")));

        assertThat(refund.refundId()).isNotBlank();
        assertThat(refund.captureId()).isEqualTo(capture.captureId());
        assertThat(refund.amount()).isEqualTo(new Money(new BigDecimal("20.00")));
    }

    @Test
    void failMode_authorizeThrowsUserFriendlyMessageWithoutStackTrace() {
        adapter.setFailMode(true);

        assertThatThrownBy(() -> adapter.authorize(order(), new PaymentMethod("card", "tok_test")))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("declined");
    }

    @Test
    void failMode_captureAndRefundThrowUserFriendlyMessages() {
        adapter.setFailMode(true);
        assertThatThrownBy(() -> adapter.capture("mock-auth-1"))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("capture");
        assertThatThrownBy(() -> adapter.refund("mock-cap-1", new Money(new BigDecimal("10.00"))))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("refund");
    }

    @Test
    void auditTrail_recordsAuthorizeCaptureRefundSequence() {
        PaymentAuthorization auth = adapter.authorize(order(), new PaymentMethod("card", "tok_test"));
        PaymentCapture capture = adapter.capture(auth.authorizationId());
        adapter.refund(capture.captureId(), new Money(new BigDecimal("10.00")));

        List<String> entries = adapter.getAuditEntries();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0)).contains("authorize", "order=order-1", "amount=");
        assertThat(entries.get(1)).contains("capture", "authorizationId=" + auth.authorizationId());
        assertThat(entries.get(2)).contains("refund", "captureId=" + capture.captureId());
    }

    @Test
    void auditTrail_neverContainsPaymentToken() {
        String token = "tok_top_secret_token_value";
        PaymentAuthorization auth = adapter.authorize(order(), new PaymentMethod("card", token));
        PaymentCapture capture = adapter.capture(auth.authorizationId());
        adapter.refund(capture.captureId(), new Money(new BigDecimal("10.00")));

        assertThat(String.join("\n", adapter.getAuditEntries())).doesNotContain(token);
    }

    @Test
    void capture_unknownAuthorizationThrows() {
        assertThatThrownBy(() -> adapter.capture("no-such-auth"))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Unknown authorization");
    }

    @Test
    void capture_blankAuthorizationThrows() {
        assertThatThrownBy(() -> adapter.capture("  "))
                .isInstanceOf(PaymentFailedException.class);
    }

    @Test
    void refund_blankCaptureIdThrows() {
        assertThatThrownBy(() -> adapter.refund("  ", new Money(new BigDecimal("10.00"))))
                .isInstanceOf(PaymentFailedException.class);
    }

    @Test
    void refund_nullAmountThrows() {
        assertThatThrownBy(() -> adapter.refund("mock-cap-1", null))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void authorize_nullMethodOrTokenThrows() {
        assertThatThrownBy(() -> adapter.authorize(order(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.authorize(order(), new PaymentMethod("card", " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
