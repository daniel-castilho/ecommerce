package com.loja.admindashboard.adapter.in.web;

import com.loja.admindashboard.domain.port.in.DashboardMetricsUseCase;
import com.loja.shared.domain.Money;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Named
@RequestScoped
public class DashboardBean {

    private static final Locale BR = new Locale("pt", "BR");

    @Inject
    private DashboardMetricsUseCase dashboardMetricsUseCase;

    private DashboardMetricsUseCase.DashboardSummary summary;

    @PostConstruct
    void load() {
        summary = dashboardMetricsUseCase.getSummary();
    }

    public DashboardMetricsUseCase.DashboardSummary getSummary() {
        return summary;
    }

    public long getTotalUsers() {
        return summary.totalUsers();
    }

    public long getTotalProducts() {
        return summary.totalProducts();
    }

    public long getTotalOrders() {
        return summary.totalOrders();
    }

    public long getNewCustomersToday() {
        return summary.newCustomersToday();
    }

    public long getNewCustomersThisMonth() {
        return summary.newCustomersThisMonth();
    }

    public long getOrdersToday() {
        return summary.orderMetrics().ordersToday();
    }

    public long getOrdersThisMonth() {
        return summary.orderMetrics().ordersThisMonth();
    }

    public String getRevenueTodayFormatted() {
        return formatCurrency(summary.orderMetrics().revenueToday());
    }

    public String getRevenueThisMonthFormatted() {
        return formatCurrency(summary.orderMetrics().revenueThisMonth());
    }

    public String getAverageOrderValueFormatted() {
        return formatCurrency(summary.orderMetrics().averageOrderValueThisMonth());
    }

    /** Order counts by status, highest first, for the dashboard table. */
    public List<OrderStatusRow> getOrdersByStatus() {
        return summary.orderMetrics().ordersByStatus().entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(e -> new OrderStatusRow(String.valueOf(e.getKey()), e.getValue()))
                .toList();
    }

    private String formatCurrency(Money money) {
        return NumberFormat.getCurrencyInstance(BR).format(money.getAmount());
    }

    public record OrderStatusRow(String status, long count) { }
}
