package com.loja.ordercheckout.domain.model;

import com.loja.ordercheckout.domain.exception.InvalidOrderStateException;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Money TEN = new Money(new BigDecimal("10.00"));
    private static final Money FIVE = new Money(new BigDecimal("5.00"));

    private static final ShippingAddress ADDRESS = new ShippingAddress(
            "Ana Souza", "Rua das Flores", "123", null, "Centro", "São Paulo", "SP",
            "01310-100", null);

    private OrderLine line(String productId, int quantity, Money unitPrice, int position) {
        return new OrderLine(productId, "Product " + productId, unitPrice, quantity, position);
    }

    private Order newPendingOrder() {
        return new Order("order-1", "user-1");
    }

    private Order authorizedOrder() {
        Order order = newPendingOrder();
        order.addItem(line("p1", 1, TEN, 0));
        order.authorize(new PaymentAuthorization("card", "auth-1", TEN, "tx-1", Instant.now()));
        return order;
    }

    private PaymentCapture capture() {
        return new PaymentCapture("auth-1", "capture-1", TEN, "tx-1", Instant.now());
    }

    private Order confirmedOrder() {
        Order order = newPendingOrder();
        order.addItem(line("p1", 1, TEN, 0));
        order.confirm();
        return order;
    }

    private Order shippedOrder() {
        Order order = confirmedOrder();
        order.process();
        order.ship("TRACK-1");
        return order;
    }

    private Order deliveredOrder() {
        Order order = shippedOrder();
        order.deliver();
        return order;
    }

    // ---- S1 acceptance criteria ----

    @Test
    void capture_whenPendingAndAuthorized_transitionsToConfirmedAndUpdatesPaymentInfo() {
        Order order = authorizedOrder();

        order.capture(capture());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentInfo().getCaptureId()).isEqualTo("capture-1");
        assertThat(order.getPaymentInfo().getCapturedAmount()).isEqualTo(TEN);
        assertThat(order.getPaymentInfo().getStatus()).isEqualTo(PaymentInfo.PaymentStatus.CAPTURED);
    }

    @Test
    void canTransitionTo_fromConfirmed_toShipped_returnsTrue() {
        Order order = authorizedOrder();
        order.capture(capture());

        assertThat(order.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    void canTransitionTo_fromDelivered_toCancelled_returnsFalse() {
        assertThat(deliveredOrder().canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void validateForCheckout_withEmptyLines_throwsInvalidOrderStateException() {
        Order order = newPendingOrder();

        assertThatThrownBy(order::validateForCheckout)
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Cart is empty");
    }

    @Test
    void requestRefund_whenConfirmed_transitionsToRefundedAndStoresRefundMetadata() {
        Order order = authorizedOrder();
        order.capture(capture());

        order.requestRefund(new Money(new BigDecimal("5.00")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getPaymentInfo().getRefundedAmount()).isEqualTo(new Money(new BigDecimal("5.00")));
        assertThat(order.getPaymentInfo().getStatus()).isEqualTo(PaymentInfo.PaymentStatus.REFUNDED);
    }

    @Test
    void lineTotal_calculatesQuantityTimesUnitPrice() {
        assertThat(line("p1", 2, TEN, 0).lineTotal().getAmount()).isEqualByComparingTo("20.00");
    }

    // ---- state machine transition matrix ----

    @Test
    void canTransitionTo_obeysTransitionMatrix() {
        assertTransitions(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertTransitions(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
                OrderStatus.CANCELLED, OrderStatus.REFUNDED, OrderStatus.REFUND_REQUESTED);
        assertTransitions(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.CANCELLED,
                OrderStatus.REFUNDED, OrderStatus.REFUND_REQUESTED);
        assertTransitions(OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED,
                OrderStatus.REFUNDED, OrderStatus.REFUND_REQUESTED);
        assertTransitions(OrderStatus.DELIVERED, OrderStatus.REFUNDED, OrderStatus.REFUND_REQUESTED);
        assertTransitions(OrderStatus.REFUND_REQUESTED, OrderStatus.REFUNDED, OrderStatus.DELIVERED);
        assertTransitions(OrderStatus.CANCELLED);
        assertTransitions(OrderStatus.REFUNDED);
    }

    private void assertTransitions(OrderStatus from, OrderStatus... allowed) {
        Order order = Order.restore("order-1", "user-1", "user-1@example.com", Instant.now(),
                from, List.of(), null, null, null, null, null);
        for (OrderStatus target : OrderStatus.values()) {
            boolean expected = Arrays.asList(allowed).contains(target);
            assertThat(order.canTransitionTo(target))
                    .as("transition %s -> %s", from, target)
                    .isEqualTo(expected);
        }
    }

    // ---- constructor / factory ----

    @Test
    void constructor_newOrder_hasPendingStatusAndNoItems() {
        Order order = newPendingOrder();

        assertThat(order.getId()).isEqualTo("order-1");
        assertThat(order.getUserId()).isEqualTo("user-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void create_buildsOrderFromLinesAndAddress() {
        Order order = Order.create("user-1", List.of(line("p1", 2, TEN, 0)), ADDRESS);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getShippingAddress()).isEqualTo(ADDRESS);
    }

    @Test
    void addItem_whenPending_addsItem() {
        Order order = newPendingOrder();

        order.addItem(line("p1", 2, TEN, 0));

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getProductId()).isEqualTo("p1");
    }

    @Test
    void addItem_whenNotPending_throwsInvalidOrderStateException() {
        Order order = confirmedOrder();

        assertThatThrownBy(() -> order.addItem(line("p2", 1, TEN, 1)))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("CONFIRMED");
    }

    // ---- legacy transitions (used by the current no-payment flow) ----

    @Test
    void confirm_withEmptyItems_throwsInvalidOrderStateException() {
        assertThatThrownBy(newPendingOrder()::confirm)
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("without items");
    }

    @Test
    void confirm_withItems_transitionsToConfirmed() {
        Order order = newPendingOrder();
        order.addItem(line("p1", 1, TEN, 0));

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancel_whenPending_transitionsToCancelled() {
        Order order = newPendingOrder();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_whenShipped_transitionsToCancelled() {
        Order order = shippedOrder();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_whenDelivered_throwsInvalidOrderStateException() {
        assertThatThrownBy(deliveredOrder()::cancel)
                .isInstanceOf(InvalidOrderStateException.class);
    }

    // ---- forward path ----

    @Test
    void ship_setsTrackingNumberAndShippedStatus() {
        Order order = shippedOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getTrackingNumber()).isEqualTo("TRACK-1");
    }

    @Test
    void process_whenNotConfirmed_throwsInvalidOrderStateException() {
        assertThatThrownBy(newPendingOrder()::process)
                .isInstanceOf(InvalidOrderStateException.class);
    }

    // ---- payment flow ----

    @Test
    void authorize_storesAuthorizationAndKeepsPending() {
        Order order = newPendingOrder();

        order.authorize(new PaymentAuthorization("card", "auth-1", TEN, "tx-1", Instant.now()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentInfo().getAuthorizationId()).isEqualTo("auth-1");
        assertThat(order.getPaymentInfo().getStatus()).isEqualTo(PaymentInfo.PaymentStatus.AUTHORIZED);
    }

    @Test
    void capture_whenNotAuthorized_throwsInvalidOrderStateException() {
        Order order = newPendingOrder();
        order.addItem(line("p1", 1, TEN, 0));

        assertThatThrownBy(() -> order.capture(new PaymentCapture("auth-x", "capture-x", TEN, "tx-1", Instant.now())))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    void capture_whenNotPending_throwsInvalidOrderStateException() {
        Order order = confirmedOrder();

        assertThatThrownBy(() -> order.capture(new PaymentCapture("auth-1", "capture-1", TEN, "tx-1", Instant.now())))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void requestRefund_whenPending_throwsInvalidOrderStateException() {
        Order order = newPendingOrder();
        order.addItem(line("p1", 1, TEN, 0));

        assertThatThrownBy(() -> order.requestRefund(TEN))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("no captured payment");
    }

    @Test
    void requestRefund_whenAmountExceedsCaptured_throwsInvalidOrderStateException() {
        Order order = authorizedOrder();
        order.capture(capture());

        assertThatThrownBy(() -> order.requestRefund(new Money(new BigDecimal("20.00"))))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("remaining balance");
    }

    @Test
    void requestRefund_afterPreviousRefund_failsAsOrderIsTerminal() {
        Order order = authorizedOrder();
        order.capture(capture());
        order.requestRefund(new Money(new BigDecimal("6.00")));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);

        assertThatThrownBy(() -> order.requestRefund(new Money(new BigDecimal("2.00"))))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    // ---- totals & immutability ----

    @Test
    void getTotal_returnsSumOfLineTotalsPlusShippingCost() {
        Order order = newPendingOrder();
        order.addItem(line("p1", 2, TEN, 0));
        order.addItem(line("p2", 3, FIVE, 1));
        order.setShippingCost(new Money(new BigDecimal("7.50")));

        assertThat(order.getTotal().getAmount()).isEqualByComparingTo("42.50");
    }

    @Test
    void getItems_returnsUnmodifiableList() {
        Order order = newPendingOrder();
        order.addItem(line("p1", 1, TEN, 0));

        List<OrderLine> items = order.getItems();

        assertThatThrownBy(() -> items.add(line("p2", 1, TEN, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- value object validation ----

    @Test
    void orderLine_withZeroQuantity_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> line("p1", 0, TEN, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void shippingAddress_withBlankStreet_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ShippingAddress("Ana", " ", "123", null, null, "São Paulo", "SP",
                "01310-100", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Street");
    }

    @Test
    void shippingAddress_withInvalidCep_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ShippingAddress("Ana", "Rua", "123", null, null, "São Paulo", "SP",
                "123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("XXXXX-XXX");
    }
}
