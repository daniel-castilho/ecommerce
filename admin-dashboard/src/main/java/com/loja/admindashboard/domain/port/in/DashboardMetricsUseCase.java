package com.loja.admindashboard.domain.port.in;

public interface DashboardMetricsUseCase {
    DashboardSummary getSummary();

    record DashboardSummary(long totalUsers, long totalProducts, long totalOrders) { }
}
