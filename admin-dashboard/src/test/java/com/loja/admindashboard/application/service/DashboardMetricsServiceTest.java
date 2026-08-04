package com.loja.admindashboard.application.service;

import com.loja.admindashboard.application.dto.DashboardSummaryDTO;
import com.loja.ordercheckout.domain.model.OrderMetrics;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.in.AdminOrderMetricsUseCase;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.in.SearchProductsUseCase;
import com.loja.shared.domain.Money;
import com.loja.useraccount.domain.port.in.CountUsersUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardMetricsServiceTest {

    private final SearchProductsUseCase searchProductsUseCase = mock(SearchProductsUseCase.class);
    private final CountUsersUseCase countUsersUseCase = mock(CountUsersUseCase.class);
    private final AdminOrderMetricsUseCase adminOrderMetricsUseCase = mock(AdminOrderMetricsUseCase.class);

    private DashboardMetricsService service;

    @BeforeEach
    void setUp() {
        service = new DashboardMetricsService(searchProductsUseCase, countUsersUseCase,
                adminOrderMetricsUseCase);
    }

    private Product product(String id) {
        return new Product(id, new Sku("SKU-" + id), new Slug("slug-" + id), "Product " + id,
                null, null, new Money(new BigDecimal("10.00")), null, 5, ProductStatus.ACTIVE,
                null, null, null, Set.of(1L), List.of());
    }

    private OrderMetrics orderMetrics(long ordersToday, long ordersThisMonth) {
        return new OrderMetrics(
                new Money(new BigDecimal("100.00")), new Money(new BigDecimal("500.00")),
                ordersToday, ordersThisMonth,
                Map.of(OrderStatus.CONFIRMED, 3L, OrderStatus.PENDING, 1L),
                new Money(new BigDecimal("25.00")));
    }

    @Test
    void getSummary_composesRealMetricsFromAllModules() {
        when(searchProductsUseCase.findAll()).thenReturn(List.of(product("p1"), product("p2"), product("p3")));
        when(countUsersUseCase.countAll()).thenReturn(12L);
        when(countUsersUseCase.countRegisteredToday()).thenReturn(1L);
        when(countUsersUseCase.countRegisteredThisMonth()).thenReturn(2L);
        when(adminOrderMetricsUseCase.countAllOrders()).thenReturn(7L);
        when(adminOrderMetricsUseCase.getOrderMetrics()).thenReturn(orderMetrics(2L, 4L));

        DashboardSummaryDTO summary = service.getSummary();

        assertThat(summary.totalProducts()).isEqualTo(3);
        assertThat(summary.totalUsers()).isEqualTo(12);
        assertThat(summary.totalOrders()).isEqualTo(7);
        assertThat(summary.newCustomersToday()).isEqualTo(1);
        assertThat(summary.newCustomersThisMonth()).isEqualTo(2);
        assertThat(summary.orderMetrics().ordersToday()).isEqualTo(2);
        assertThat(summary.orderMetrics().ordersThisMonth()).isEqualTo(4);
        assertThat(summary.orderMetrics().revenueToday()).isEqualTo(new Money(new BigDecimal("100.00")));
        assertThat(summary.orderMetrics().ordersByStatus()).containsEntry(OrderStatus.CONFIRMED, 3L);
        verify(searchProductsUseCase).findAll();
        verify(countUsersUseCase).countAll();
        verify(countUsersUseCase).countRegisteredToday();
        verify(countUsersUseCase).countRegisteredThisMonth();
        verify(adminOrderMetricsUseCase).countAllOrders();
        verify(adminOrderMetricsUseCase).getOrderMetrics();
    }

    @Test
    void getSummary_withEmptyStores_returnsZero() {
        when(searchProductsUseCase.findAll()).thenReturn(List.of());
        when(countUsersUseCase.countAll()).thenReturn(0L);
        when(countUsersUseCase.countRegisteredToday()).thenReturn(0L);
        when(countUsersUseCase.countRegisteredThisMonth()).thenReturn(0L);
        when(adminOrderMetricsUseCase.countAllOrders()).thenReturn(0L);
        when(adminOrderMetricsUseCase.getOrderMetrics()).thenReturn(orderMetrics(0L, 0L));

        DashboardSummaryDTO summary = service.getSummary();

        assertThat(summary.totalProducts()).isZero();
        assertThat(summary.totalUsers()).isZero();
        assertThat(summary.totalOrders()).isZero();
        assertThat(summary.newCustomersToday()).isZero();
        assertThat(summary.newCustomersThisMonth()).isZero();
        assertThat(summary.orderMetrics().ordersToday()).isZero();
        assertThat(summary.orderMetrics().revenueThisMonth()).isEqualTo(new Money(new BigDecimal("500.00")));
    }
}
