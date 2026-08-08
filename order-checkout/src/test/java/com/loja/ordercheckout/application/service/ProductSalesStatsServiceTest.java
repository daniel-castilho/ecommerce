package com.loja.ordercheckout.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.loja.ordercheckout.domain.model.ProductSalesAggregate;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.shared.domain.Money;

class ProductSalesStatsServiceTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final ProductSalesStatsService service = new ProductSalesStatsService(orderRepository);

    @Test
    void salesByProductId_keysByProductAndDelegatesToPort() {
        when(orderRepository.productSales()).thenReturn(List.of(
                new ProductSalesAggregate("p1", 10L, new Money(new BigDecimal("100.00"))),
                new ProductSalesAggregate("p2", 3L, new Money(new BigDecimal("30.00")))));

        var sales = service.salesByProductId();

        assertThat(sales).containsOnlyKeys("p1", "p2");
        assertThat(sales.get("p1").unitsSold()).isEqualTo(10L);
        assertThat(sales.get("p1").revenue().getAmount()).isEqualByComparingTo("100.00");
        verify(orderRepository).productSales();
    }

    @Test
    void salesByProductId_emptyWhenNoSales() {
        when(orderRepository.productSales()).thenReturn(List.of());

        assertThat(service.salesByProductId()).isEmpty();
    }
}
