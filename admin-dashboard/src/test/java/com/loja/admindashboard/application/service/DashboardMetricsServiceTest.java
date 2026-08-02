package com.loja.admindashboard.application.service;

import com.loja.admindashboard.domain.port.in.DashboardMetricsUseCase.DashboardSummary;
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

    @Test
    void getSummary_composesRealMetricsFromAllModules() {
        when(searchProductsUseCase.findAll()).thenReturn(List.of(product("p1"), product("p2"), product("p3")));
        when(countUsersUseCase.countAll()).thenReturn(12L);
        when(adminOrderMetricsUseCase.countAllOrders()).thenReturn(7L);

        DashboardSummary summary = service.getSummary();

        assertThat(summary.totalProducts()).isEqualTo(3);
        assertThat(summary.totalUsers()).isEqualTo(12);
        assertThat(summary.totalOrders()).isEqualTo(7);
        verify(searchProductsUseCase).findAll();
        verify(countUsersUseCase).countAll();
        verify(adminOrderMetricsUseCase).countAllOrders();
    }

    @Test
    void getSummary_withEmptyStores_returnsZero() {
        when(searchProductsUseCase.findAll()).thenReturn(List.of());
        when(countUsersUseCase.countAll()).thenReturn(0L);
        when(adminOrderMetricsUseCase.countAllOrders()).thenReturn(0L);

        DashboardSummary summary = service.getSummary();

        assertThat(summary.totalProducts()).isZero();
        assertThat(summary.totalUsers()).isZero();
        assertThat(summary.totalOrders()).isZero();
    }
}
