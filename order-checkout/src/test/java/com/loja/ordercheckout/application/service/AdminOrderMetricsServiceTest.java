package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderMetricsServiceTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);

    @Test
    void countAllOrders_delegatesToRepository() {
        when(orderRepository.countAll()).thenReturn(7L);
        AdminOrderMetricsService service = new AdminOrderMetricsService(orderRepository);

        long count = service.countAllOrders();

        assertThat(count).isEqualTo(7L);
        verify(orderRepository).countAll();
    }

    @Test
    void countAllOrders_withEmptyStore_returnsZero() {
        when(orderRepository.countAll()).thenReturn(0L);
        AdminOrderMetricsService service = new AdminOrderMetricsService(orderRepository);

        assertThat(service.countAllOrders()).isZero();
    }
}
