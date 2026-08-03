package com.loja.admindashboard.application.service;

import com.loja.admindashboard.domain.port.in.DashboardMetricsUseCase;
import com.loja.ordercheckout.domain.port.in.AdminOrderMetricsUseCase;
import com.loja.productcatalog.domain.port.in.SearchProductsUseCase;
import com.loja.useraccount.domain.port.in.CountUsersUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The admin-dashboard module has NO business logic of its own: it only
 * composes use cases (input ports) from other modules to display
 * metrics. This avoids duplicating logic that already exists in user-account,
 * product-catalog, and order-checkout (DRY / Clean Code).
 */
@ApplicationScoped
public class DashboardMetricsService implements DashboardMetricsUseCase {

    private final SearchProductsUseCase searchProductsUseCase;
    private final CountUsersUseCase countUsersUseCase;
    private final AdminOrderMetricsUseCase adminOrderMetricsUseCase;

    @Inject
    public DashboardMetricsService(SearchProductsUseCase searchProductsUseCase,
                                   CountUsersUseCase countUsersUseCase,
                                   AdminOrderMetricsUseCase adminOrderMetricsUseCase) {
        this.searchProductsUseCase = searchProductsUseCase;
        this.countUsersUseCase = countUsersUseCase;
        this.adminOrderMetricsUseCase = adminOrderMetricsUseCase;
    }

    @Override
    public DashboardSummary getSummary() {
        long totalProducts = searchProductsUseCase.findAll().size();
        long totalUsers = countUsersUseCase.countAll();
        long totalOrders = adminOrderMetricsUseCase.countAllOrders();
        return new DashboardSummary(
                totalUsers,
                totalProducts,
                totalOrders,
                countUsersUseCase.countRegisteredToday(),
                countUsersUseCase.countRegisteredThisMonth(),
                adminOrderMetricsUseCase.getOrderMetrics());
    }
}
