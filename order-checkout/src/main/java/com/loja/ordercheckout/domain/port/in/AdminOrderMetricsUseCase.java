package com.loja.ordercheckout.domain.port.in;

/** Input port (admin-only): aggregate order counts for the admin dashboard. */
public interface AdminOrderMetricsUseCase {

    /** Total number of persisted orders across all customers and statuses. */
    long countAllOrders();
}
