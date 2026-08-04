package com.loja.ordercheckout.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.exception.OrderNotFoundException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.PaymentGatewayPort;
import com.loja.ordercheckout.domain.port.out.RefundRequestRepositoryPort;
import com.loja.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefundApplicationServiceTest {

    private RefundRequestRepositoryPort refundRepository;
    private OrderRepositoryPort orderRepository;
    private PaymentGatewayPort paymentGateway;
    private RefundApplicationService service;

    @BeforeEach
    void setUp() {
        refundRepository = mock(RefundRequestRepositoryPort.class);
        orderRepository = mock(OrderRepositoryPort.class);
        paymentGateway = mock(PaymentGatewayPort.class);
        service = new RefundApplicationService(refundRepository, orderRepository, paymentGateway);
    }

    @Test
    void requestRefund_savesRefundAndMarksOrderRefundRequested() {
        Order order = orderIn(OrderStatus.DELIVERED);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(order));

        service.requestRefund("o-1", new Money(new BigDecimal("50.00")), "Damaged item");

        verify(refundRepository).save(org.mockito.ArgumentMatchers.any(RefundRequest.class));
        verify(orderRepository).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    }

    @Test
    void requestRefund_whenOrderMissing_throwsOrderNotFoundException() {
        when(orderRepository.findById("o-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestRefund("o-1", new Money(new BigDecimal("50.00")), "reason"))
                .isInstanceOf(OrderNotFoundException.class);
        verifyNoInteractions(refundRepository);
    }

    @Test
    void approveRefund_whenGatewaySucceeds_marksProcessedAndOrderRefunded() {
        Order order = orderIn(OrderStatus.REFUND_REQUESTED);
        RefundRequest request = RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item");
        when(refundRepository.findById("r-1")).thenReturn(Optional.of(request));
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(order));
        when(paymentGateway.processRefund(request)).thenReturn(true);

        service.approveRefund("r-1");

        assertThat(request.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(refundRepository, org.mockito.Mockito.times(2)).save(request);
        verify(orderRepository).save(order);
    }

    @Test
    void approveRefund_whenGatewayFails_throws() {
        Order order = orderIn(OrderStatus.REFUND_REQUESTED);
        RefundRequest request = RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item");
        when(refundRepository.findById("r-1")).thenReturn(Optional.of(request));
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(order));
        when(paymentGateway.processRefund(request)).thenReturn(false);

        assertThatThrownBy(() -> service.approveRefund("r-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void rejectRefund_marksRejectedAndRestoresDelivered() {
        Order order = orderIn(OrderStatus.REFUND_REQUESTED);
        RefundRequest request = RefundRequest.request("o-1", new Money(new BigDecimal("50.00")), "Damaged item");
        when(refundRepository.findById("r-1")).thenReturn(Optional.of(request));
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(order));

        service.rejectRefund("r-1", "Policy violation");

        assertThat(request.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("Policy violation");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(orderRepository).save(order);
    }

    @Test
    void listRefundRequests_withoutStatus_delegatesToFindAll() {
        PageResult<RefundRequest> expected = new PageResult<>(List.of(), 0L, 0, 20);
        when(refundRepository.findAll(0, 20)).thenReturn(expected);

        PageResult<RefundRequest> actual = service.listRefundRequests(null, 0, 20);

        assertThat(actual).isSameAs(expected);
        verify(refundRepository).findAll(0, 20);
    }

    @Test
    void listRefundRequests_withStatus_delegatesToFindByStatus() {
        PageResult<RefundRequest> expected = new PageResult<>(List.of(), 0L, 0, 20);
        when(refundRepository.findByStatus(RefundStatus.PENDING, 0, 20)).thenReturn(expected);

        PageResult<RefundRequest> actual = service.listRefundRequests(RefundStatus.PENDING, 0, 20);

        assertThat(actual).isSameAs(expected);
        verify(refundRepository).findByStatus(RefundStatus.PENDING, 0, 20);
    }

    private static Order orderIn(OrderStatus status) {
        return Order.restore("o-1", "u-1", "customer@example.com", Instant.now(),
                status, List.of(), null, null, null, null, null);
    }
}
