package com.loja.admindashboard.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;

class OrderListServiceTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);

    private OrderListService service;

    @BeforeEach
    void setUp() {
        service = new OrderListService(orderRepository);
    }

    @Test
    void listOrders_delegatesToRepositoryAndReturnsPageResult() {
        Order order = new Order("order-1", "user-1", "customer@example.com");
        PageResult<Order> expected = new PageResult<>(List.of(order), 1L, 0, 20);
        when(orderRepository.findAll(0, 20)).thenReturn(expected);

        PageResult<Order> result = service.listOrders(0, 20);

        assertThat(result).isEqualTo(expected);
        verify(orderRepository).findAll(0, 20);
    }

    @Test
    void listOrders_withStatusFilter_delegatesToRepository() {
        Order order = new Order("order-2", "user-2", "customer@example.com");
        PageResult<Order> expected = new PageResult<>(List.of(order), 1L, 0, 20);
        when(orderRepository.findByStatus(OrderStatus.CONFIRMED, 0, 20)).thenReturn(expected);

        PageResult<Order> result = service.listOrders(OrderStatus.CONFIRMED, 0, 20);

        assertThat(result).isEqualTo(expected);
        verify(orderRepository).findByStatus(OrderStatus.CONFIRMED, 0, 20);
    }
}
