package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.domain.model.OrderMetrics;

/** Input port (admin-only): aggregate order metrics for the admin dashboard. */
public interface AdminOrderMetricsUseCase {

    /** Total number of persisted orders across all customers and statuses. */
    long countAllOrders();

    /**
     * Aggregated order KPIs for the admin dashboard: revenue and order counts for
     * today and the current month, the status breakdown, and the monthly average
     * order value.
     */
    OrderMetrics getOrderMetrics();
}
