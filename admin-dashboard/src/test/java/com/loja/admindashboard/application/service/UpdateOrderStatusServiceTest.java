package com.loja.admindashboard.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.NotificationPort;

class UpdateOrderStatusServiceTest {

    @Test
    void updateStatus_transitionsOrderAndPersistsIt() {
        OrderRepositoryPort orderRepository = org.mockito.Mockito.mock(OrderRepositoryPort.class);
        NotificationPort notificationPort = org.mockito.Mockito.mock(NotificationPort.class);
        UpdateOrderStatusService service = new UpdateOrderStatusService(orderRepository, notificationPort);

        Order order = Order.restore("order-1", "customer-1", "customer@example.com", Instant.now(),
                OrderStatus.CONFIRMED, List.of(), null, null, null, null, Instant.now(), 0L);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order updated = service.updateStatus("order-1", OrderStatus.PROCESSING, "TRACK-123");

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderRepository).save(order);
    }
}
