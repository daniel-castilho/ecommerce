package com.loja.ordercheckout.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderRevenueReport;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.ProductSalesAggregate;
import com.loja.shared.domain.Money;

public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(String id);
    PageResult<Order> findByCustomerId(String customerId, int page, int pageSize);
    PageResult<Order> findAll(int page, int pageSize);
    PageResult<Order> findByStatus(OrderStatus status, int page, int pageSize);
    List<Order> findByStatus(OrderStatus status);

    /** Total number of persisted orders (admin metrics). */
    long countAll();

    /**
     * Sum of item and shipping revenue for orders created at or after {@code since},
     * excluding CANCELLED and REFUNDED orders (admin metrics).
     */
    Money revenueSince(Instant since);

    /** Number of orders created at or after {@code since}, across all statuses (admin metrics). */
    long countCreatedSince(Instant since);

    /** Order counts grouped by status; every status is present, missing ones zero-filled (admin metrics). */
    Map<OrderStatus, Long> countByStatus();

    /**
     * Revenue report for orders created in {@code [from, to)}, excluding CANCELLED
     * and REFUNDED orders. The time series is bucketed per local date using the
     * system default zone (admin reporting, backlog S20).
     */
    OrderRevenueReport revenueReport(Instant from, Instant to);

    /**
     * All-time per-product sales aggregates over order lines, excluding CANCELLED
     * and REFUNDED orders. Only products that actually sold appear (admin reporting,
     * backlog S21 — the report service merges this with the product catalog).
     */
    List<ProductSalesAggregate> productSales();
}
