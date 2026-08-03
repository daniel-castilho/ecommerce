package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.model.OrderMetrics;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.in.AdminOrderMetricsUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

    @Test
    void getOrderMetrics_composesRepoAggregatesAndComputesAverageOrderValue() {
        when(orderRepository.revenueSince(any())).thenReturn(new Money(new BigDecimal("100.00")));
        when(orderRepository.countCreatedSince(any())).thenReturn(4L);
        when(orderRepository.countByStatus()).thenReturn(Map.of(OrderStatus.CONFIRMED, 4L));
        AdminOrderMetricsService service = new AdminOrderMetricsService(orderRepository);

        OrderMetrics metrics = service.getOrderMetrics();

        assertThat(metrics.revenueToday()).isEqualTo(new Money(new BigDecimal("100.00")));
        assertThat(metrics.revenueThisMonth()).isEqualTo(new Money(new BigDecimal("100.00")));
        assertThat(metrics.ordersToday()).isEqualTo(4);
        assertThat(metrics.ordersThisMonth()).isEqualTo(4);
        assertThat(metrics.ordersByStatus()).containsEntry(OrderStatus.CONFIRMED, 4L);
        assertThat(metrics.averageOrderValueThisMonth()).isEqualTo(new Money(new BigDecimal("25.00")));
        verify(orderRepository, times(2)).revenueSince(any(Instant.class));
        verify(orderRepository, times(2)).countCreatedSince(any(Instant.class));
        verify(orderRepository).countByStatus();
    }

    @Test
    void getOrderMetrics_withNoOrdersThisMonth_averageOrderValueIsZero() {
        when(orderRepository.revenueSince(any())).thenReturn(Money.zero());
        when(orderRepository.countCreatedSince(any())).thenReturn(0L);
        when(orderRepository.countByStatus()).thenReturn(Map.of());
        AdminOrderMetricsService service = new AdminOrderMetricsService(orderRepository);

        OrderMetrics metrics = service.getOrderMetrics();

        assertThat(metrics.revenueToday()).isEqualTo(Money.zero());
        assertThat(metrics.ordersThisMonth()).isZero();
        assertThat(metrics.averageOrderValueThisMonth()).isEqualTo(Money.zero());
    }
}
