package com.loja.admindashboard.application.service;

import com.loja.admindashboard.domain.port.in.DashboardMetricsUseCase;
import com.loja.productcatalog.domain.port.in.SearchProductsUseCase;
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

    @Inject
    public DashboardMetricsService(SearchProductsUseCase searchProductsUseCase) {
        this.searchProductsUseCase = searchProductsUseCase;
    }

    @Override
    public DashboardSummary getSummary() {
        long totalProducts = searchProductsUseCase.findAll().size();
        return new DashboardSummary(0, totalProducts, 0);
    }
}
