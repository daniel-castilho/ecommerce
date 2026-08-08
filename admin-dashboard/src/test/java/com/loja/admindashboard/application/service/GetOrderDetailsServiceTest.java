package com.loja.admindashboard.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.admindashboard.application.dto.OrderDetailsDTO;
import com.loja.admindashboard.application.dto.OrderTimelineEntryDTO;
import com.loja.admindashboard.application.dto.PaymentTransactionDTO;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.PaymentAuthorization;
import com.loja.ordercheckout.domain.model.PaymentCapture;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.RefundRequestRepositoryPort;
import com.loja.shared.domain.Money;

class GetOrderDetailsServiceTest {

    private static final Instant AUTHORIZED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant CAPTURED_AT = Instant.parse("2026-01-01T10:05:00Z");
    private static final Instant REFUND_PROCESSED_AT = Instant.parse("2026-01-03T14:00:00Z");

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final RefundRequestRepositoryPort refundRequestRepository = mock(RefundRequestRepositoryPort.class);

    private GetOrderDetailsService service;

    @BeforeEach
    void setUp() {
        service = new GetOrderDetailsService(orderRepository, refundRequestRepository);
    }

    private Order confirmedAndRefundedOrder() {
        Order order = new Order("o-1", "user-1");
        order.addItem(new OrderLine("p1", "Product A", new Money(new BigDecimal("10.00")), 2, 0));
        order.setShippingCost(new Money(new BigDecimal("5.00")));
        order.authorize(new PaymentAuthorization("card", "auth-1",
                new Money(new BigDecimal("25.00")), "gw-1", AUTHORIZED_AT));
        order.capture(new PaymentCapture("auth-1", "capture-1",
                new Money(new BigDecimal("25.00")), "gw-1", CAPTURED_AT));
        return order;
    }

    private RefundRequest processedRefund() {
        return RefundRequest.reconstitute("r-1", "o-1", new Money(new BigDecimal("10.00")),
                "Wrong size", RefundStatus.PROCESSED, null,
                Instant.parse("2026-01-02T09:00:00Z"), REFUND_PROCESSED_AT);
    }

    @Test
    void findById_mapsTimelineAndPaymentTransactions() {
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(confirmedAndRefundedOrder()));
        when(refundRequestRepository.findByOrderId("o-1")).thenReturn(List.of(processedRefund()));

        Optional<OrderDetailsDTO> result = service.findById("o-1");

        assertThat(result).isPresent();
        assertThat(result.get().getTimeline()).extracting(OrderTimelineEntryDTO::status)
                .containsExactly(OrderStatus.PENDING, OrderStatus.CONFIRMED);
        assertThat(result.get().getTimeline()).extracting(OrderTimelineEntryDTO::label)
                .contains("Order placed", "Status changed to CONFIRMED");
        assertThat(result.get().getPayments()).extracting(PaymentTransactionDTO::type)
                .containsExactly("AUTHORIZATION", "CAPTURE", "REFUND_PROCESSED");
        assertThat(result.get().getPayments().get(0).reference()).isEqualTo("gw-1");
        assertThat(result.get().getPayments().get(0).occurredAt()).isEqualTo(AUTHORIZED_AT);
        assertThat(result.get().getPayments().get(2).amount()).isEqualTo(new Money(new BigDecimal("10.00")));
        assertThat(result.get().getPayments().get(2).occurredAt()).isEqualTo(REFUND_PROCESSED_AT);
        assertThat(result.get().getPayments().get(2).reference()).isEqualTo("Wrong size");
    }

    @Test
    void findById_whenOrderHasNoPaymentInfo_onlyIncludesRefundTransactions() {
        Order order = new Order("o-2", "user-2");
        order.addItem(new OrderLine("p1", "Product A", new Money(new BigDecimal("10.00")), 1, 0));
        order.confirm();
        when(orderRepository.findById("o-2")).thenReturn(Optional.of(order));
        when(refundRequestRepository.findByOrderId("o-2"))
                .thenReturn(List.of(RefundRequest.reconstitute("r-2", "o-2",
                        new Money(new BigDecimal("5.00")), "Damaged", RefundStatus.REJECTED,
                        "Not eligible", Instant.parse("2026-01-02T09:00:00Z"), null)));

        Optional<OrderDetailsDTO> result = service.findById("o-2");

        assertThat(result).isPresent();
        assertThat(result.get().getPayments()).extracting(PaymentTransactionDTO::type)
                .containsExactly("REFUND_REJECTED");
        assertThat(result.get().getPayments().get(0).occurredAt())
                .isEqualTo(Instant.parse("2026-01-02T09:00:00Z"));
    }

    @Test
    void findById_whenOrderHasNoTimelineHistory_derivesPlacementEntry() {
        Instant createdAt = Instant.parse("2026-01-01T09:00:00Z");
        Order order = Order.restore("o-3", "user-3", "customer@example.com", createdAt,
                OrderStatus.CONFIRMED, List.of(), null, null, null, null,
                Instant.parse("2026-01-01T09:10:00Z"));
        when(orderRepository.findById("o-3")).thenReturn(Optional.of(order));
        when(refundRequestRepository.findByOrderId("o-3")).thenReturn(List.of());

        Optional<OrderDetailsDTO> result = service.findById("o-3");

        assertThat(result).isPresent();
        assertThat(result.get().getTimeline()).hasSize(1);
        assertThat(result.get().getTimeline().get(0).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.get().getTimeline().get(0).label()).isEqualTo("Order placed");
        assertThat(result.get().getTimeline().get(0).occurredAt()).isEqualTo(createdAt);
        assertThat(result.get().getPayments()).isEmpty();
    }

    @Test
    void findById_whenOrderMissing_returnsEmpty() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(service.findById("missing")).isEmpty();
    }
}
