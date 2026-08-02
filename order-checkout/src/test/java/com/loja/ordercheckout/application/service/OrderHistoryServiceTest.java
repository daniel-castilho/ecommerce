package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.exception.InvalidOrderStateException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.PaymentAuthorization;
import com.loja.ordercheckout.domain.model.PaymentCapture;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.port.out.NotificationPort;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.productcatalog.domain.port.out.InventoryReservationPort;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderHistoryServiceTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final InventoryReservationPort inventoryReservation = mock(InventoryReservationPort.class);

    private OrderHistoryService service;
    private final Map<String, Order> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new OrderHistoryService(orderRepository, notification, inventoryReservation);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            store.put(saved.getId(), saved);
            return saved;
        });
        when(orderRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
    }

    private ShippingAddress address() {
        return new ShippingAddress("Ana Souza", "Rua das Flores", "123", null,
                "Centro", "Sao Paulo", "SP", "01310-100", null);
    }

    private Order pendingOrder(String id, String userId) {
        Order order = new Order(id, userId, "ana@example.com");
        order.addItem(new OrderLine("p1", "Product 1", new Money(new BigDecimal("10.00")), 2, 0));
        order.setShippingAddress(address());
        order.setShippingCost(new Money(new BigDecimal("15.00")));
        store.put(id, order);
        return order;
    }

    private Order capturedOrder(String id, String userId, String capturedAmount) {
        Order order = pendingOrder(id, userId);
        order.authorize(new PaymentAuthorization("card", "auth-" + id,
                new Money(new BigDecimal(capturedAmount)), "tx-" + id, Instant.now()));
        order.capture(new PaymentCapture("auth-" + id, "capture-" + id,
                new Money(new BigDecimal(capturedAmount)), "tx-" + id, Instant.now()));
        store.put(id, order);
        return order;
    }

    // ---- listing ----

    @Test
    void listByCustomer_delegatesToRepositoryWithPaging() {
        PageResult<Order> expected = new PageResult<>(List.of(), 0, 0, 20);
        when(orderRepository.findByCustomerId("user-1", 1, 20)).thenReturn(expected);

        PageResult<Order> result = service.listByCustomer("user-1", 1, 20);

        assertThat(result).isSameAs(expected);
        verify(orderRepository).findByCustomerId("user-1", 1, 20);
    }

    // ---- detail ----

    @Test
    void findById_ownedOrder_returnsOrder() {
        pendingOrder("o1", "user-1");

        Optional<Order> result = service.findById("o1", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("o1");
    }

    @Test
    void findById_foreignOrder_returnsEmpty() {
        pendingOrder("o1", "user-1");

        assertThat(service.findById("o1", "user-2")).isEmpty();
    }

    // ---- cancel ----

    @Test
    void cancel_ownedPendingOrder_cancelsSavesAndReleasesReservation() {
        pendingOrder("o1", "user-1");

        Order cancelled = service.cancel("o1", "user-1");

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryReservation).release("o1");
        verify(orderRepository).save(cancelled);
    }

    @Test
    void cancel_foreignOrder_throwsAndDoesNotSave() {
        pendingOrder("o1", "user-1");

        assertThatThrownBy(() -> service.cancel("o1", "user-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancel_missingOrder_throws() {
        assertThatThrownBy(() -> service.cancel("nope", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ---- refund ----

    @Test
    void requestRefund_capturedOrder_refundsFullBalanceAndNotifies() {
        capturedOrder("o1", "user-1", "100.00");

        Order refunded = service.requestRefund("o1", "user-1", "Too expensive");

        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refunded.getPaymentInfo().getRefundedAmount()).isEqualTo(new Money(new BigDecimal("100.00")));
        assertThat(refunded.getPaymentInfo().getRefundableAmount()).isEqualTo(Money.zero());
        verify(orderRepository).save(refunded);
        verify(notification).notifyRefundRequested(refunded, "Too expensive");
    }

    @Test
    void requestRefund_unpaidOrder_throwsInvalidOrderState() {
        Order order = pendingOrder("o1", "user-1");
        order.confirm();

        assertThatThrownBy(() -> service.requestRefund("o1", "user-1", "why"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("captured");
        verify(notification, never()).notifyRefundRequested(any(), anyString());
    }

    @Test
    void requestRefund_foreignOrder_throws() {
        capturedOrder("o1", "user-1", "100.00");

        assertThatThrownBy(() -> service.requestRefund("o1", "user-2", "why"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(notification, never()).notifyRefundRequested(any(), anyString());
    }
}
