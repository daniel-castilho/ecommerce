package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.adapter.out.persistence.OrderJpaEntity.OrderLineEmbeddable;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.PaymentAuthorization;
import com.loja.ordercheckout.domain.model.PaymentCapture;
import com.loja.ordercheckout.domain.model.PaymentInfo;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderJpaMapperTest {

    private OrderLine line(String productId, String productName, int quantity, String price, int position) {
        return new OrderLine(productId, productName, new Money(new BigDecimal(price)), quantity, position);
    }

    private Order newConfirmedOrder() {
        Order order = new Order("order-1", "user-1");
        order.addItem(line("p1", "Product A", 2, "10.00", 0));
        order.addItem(line("p2", "Product B", 3, "5.50", 1));
        order.confirm();
        return order;
    }

    @Test
    void shouldRoundTripConfirmedOrder() {
        Order original = newConfirmedOrder();

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getId()).isEqualTo("order-1");
        assertThat(restored.getUserId()).isEqualTo("user-1");
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(restored.getItems()).hasSize(2);
        OrderLine first = restored.getItems().get(0);
        assertThat(first.getProductId()).isEqualTo("p1");
        assertThat(first.getProductName()).isEqualTo("Product A");
        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(first.getPosition()).isZero();
        assertThat(first.getUnitPrice()).isEqualTo(new Money(new BigDecimal("10.00")));
        assertThat(restored.getItems().get(1).getUnitPrice()).isEqualTo(new Money(new BigDecimal("5.50")));
        assertThat(restored.getTotal().getAmount()).isEqualByComparingTo("36.50");
    }

    @Test
    void shouldRoundTripCustomerEmail() {
        Order original = new Order("order-7", "user-7", "ana@example.com");
        original.addItem(line("p1", "Product A", 1, "10.00", 0));
        original.confirm();

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getCustomerEmail()).isEqualTo("ana@example.com");
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shouldRoundTripPendingOrder() {
        Order original = new Order("order-2", "user-2");
        original.addItem(line("p1", "Product A", 1, "7.25", 0));

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(restored.getItems()).hasSize(1);
    }

    @Test
    void shouldRoundTripCancelledOrder() {
        Order original = new Order("order-3", "user-3");
        original.addItem(line("p1", "Product A", 1, "7.25", 0));
        original.cancel();

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(restored.getItems()).hasSize(1);
    }

    @Test
    void embeddableShouldRoundTripLineWithNameAndPosition() {
        OrderLine line = line("p9", "Product Nine", 4, "3.99", 3);

        OrderLine restored = OrderLineEmbeddable.fromDomain(line).toDomain();

        assertThat(restored.getProductId()).isEqualTo("p9");
        assertThat(restored.getProductName()).isEqualTo("Product Nine");
        assertThat(restored.getQuantity()).isEqualTo(4);
        assertThat(restored.getUnitPrice()).isEqualTo(new Money(new BigDecimal("3.99")));
        assertThat(restored.getPosition()).isEqualTo(3);
        assertThat(restored.lineTotal().getAmount()).isEqualByComparingTo("15.96");
    }

    @Test
    void shouldRoundTripFullAggregateIncludingAddressAndPayment() {
        Order original = new Order("order-4", "user-4");
        original.addItem(line("p1", "Product A", 2, "10.00", 0));
        original.setShippingAddress(new ShippingAddress("Ana Souza", "Rua das Flores", "123", null,
                "Centro", "São Paulo", "SP", "01310-100", null));
        original.setShippingCost(new Money(new BigDecimal("15.00")));
        original.authorize(new PaymentAuthorization("card", "auth-1",
                new Money(new BigDecimal("35.00")), "tx-1", Instant.now()));
        original.capture(new PaymentCapture("auth-1", "capture-1",
                new Money(new BigDecimal("35.00")), "tx-1", Instant.now()));

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(restored.getShippingAddress()).isNotNull();
        assertThat(restored.getShippingAddress().getPostalCode()).isEqualTo("01310-100");
        assertThat(restored.getShippingAddress().getCity()).isEqualTo("São Paulo");
        assertThat(restored.getShippingCost().getAmount()).isEqualByComparingTo("15.00");
        assertThat(restored.getPaymentInfo()).isNotNull();
        assertThat(restored.getPaymentInfo().getCaptureId()).isEqualTo("capture-1");
        assertThat(restored.getPaymentInfo().getGatewayTransactionId()).isEqualTo("tx-1");
        assertThat(restored.getPaymentInfo().getStatus()).isEqualTo(PaymentInfo.PaymentStatus.CAPTURED);
        assertThat(restored.getPaymentInfo().getCapturedAmount().getAmount()).isEqualByComparingTo("35.00");
        assertThat(restored.getTotal().getAmount()).isEqualByComparingTo("35.00");
    }

    @Test
    void shouldRoundTripShippedOrderWithTrackingNumber() {
        Order original = new Order("order-5", "user-5");
        original.addItem(line("p1", "Product A", 1, "10.00", 0));
        original.confirm();
        original.process();
        original.ship("TRACK-1");

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(restored.getTrackingNumber()).isEqualTo("TRACK-1");
    }

    @Test
    void shouldRoundTripRefundedOrderWithRefundMetadata() {
        Order original = new Order("order-6", "user-6");
        original.addItem(line("p1", "Product A", 1, "10.00", 0));
        original.authorize(new PaymentAuthorization("pix", "auth-1",
                new Money(new BigDecimal("10.00")), "tx-1", Instant.now()));
        original.capture(new PaymentCapture("auth-1", "capture-1",
                new Money(new BigDecimal("10.00")), "tx-1", Instant.now()));
        original.requestRefund(new Money(new BigDecimal("10.00")));

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(restored.getPaymentInfo().getStatus()).isEqualTo(PaymentInfo.PaymentStatus.REFUNDED);
        assertThat(restored.getPaymentInfo().getRefundedAmount().getAmount()).isEqualByComparingTo("10.00");
    }
}
