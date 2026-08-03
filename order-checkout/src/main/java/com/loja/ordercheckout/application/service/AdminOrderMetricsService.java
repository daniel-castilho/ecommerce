package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.model.OrderMetrics;
import com.loja.ordercheckout.domain.port.in.AdminOrderMetricsUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.shared.domain.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Admin-facing aggregate metrics over persisted orders. Depends only on the
 * repository port (DIP); used by the admin-dashboard module.
 */
@ApplicationScoped
public class AdminOrderMetricsService implements AdminOrderMetricsUseCase {

    private final OrderRepositoryPort orderRepository;

    @Inject
    public AdminOrderMetricsService(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public long countAllOrders() {
        return orderRepository.countAll();
    }

    @Override
    public OrderMetrics getOrderMetrics() {
        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        Money revenueToday = orderRepository.revenueSince(todayStart);
        Money revenueThisMonth = orderRepository.revenueSince(monthStart);
        long ordersToday = orderRepository.countCreatedSince(todayStart);
        long ordersThisMonth = orderRepository.countCreatedSince(monthStart);

        Money averageOrderValueThisMonth = ordersThisMonth == 0
                ? Money.zero()
                : new Money(revenueThisMonth.getAmount()
                        .divide(BigDecimal.valueOf(ordersThisMonth), 2, RoundingMode.HALF_UP));

        return new OrderMetrics(revenueToday, revenueThisMonth, ordersToday, ordersThisMonth,
                orderRepository.countByStatus(), averageOrderValueThisMonth);
    }
}
