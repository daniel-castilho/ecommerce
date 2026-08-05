package com.loja.ordercheckout.application.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.loja.ordercheckout.domain.model.OrderRevenueReport;
import com.loja.ordercheckout.domain.model.ReportGranularity;
import com.loja.ordercheckout.domain.model.RevenuePoint;
import com.loja.ordercheckout.domain.port.in.RevenueReportUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.shared.domain.Money;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Revenue report over a date range. Depends only on the repository port (DIP);
 * used by the admin-dashboard module (backlog S20). The repository always returns
 * the daily series; weekly/monthly granularities are rolled up here so bucketing
 * rules live in one place.
 */
@ApplicationScoped
public class RevenueReportService implements RevenueReportUseCase {

    private final OrderRepositoryPort orderRepository;

    @Inject
    public RevenueReportService(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderRevenueReport revenueReport(Instant from, Instant to, ReportGranularity granularity) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Report range must not be null");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Report 'from' must not be after 'to'");
        }
        OrderRevenueReport daily = orderRepository.revenueReport(from, to);
        if (granularity == null || granularity == ReportGranularity.DAILY) {
            return daily;
        }
        return rollUp(daily, granularity);
    }

    private OrderRevenueReport rollUp(OrderRevenueReport daily, ReportGranularity granularity) {
        Map<LocalDate, BigDecimal> buckets = new LinkedHashMap<>();
        for (RevenuePoint point : daily.dailySeries()) {
            LocalDate key = switch (granularity) {
                case WEEKLY -> point.date().with(DayOfWeek.MONDAY);
                case MONTHLY -> point.date().withDayOfMonth(1);
                case DAILY -> point.date();
            };
            buckets.merge(key, point.revenue().getAmount(), BigDecimal::add);
        }
        List<RevenuePoint> series = buckets.entrySet().stream()
                .map(entry -> new RevenuePoint(entry.getKey(), new Money(entry.getValue())))
                .toList();
        return new OrderRevenueReport(daily.totalRevenue(), daily.itemsRevenue(), daily.shippingRevenue(),
                daily.orderCount(), daily.averageOrderValue(), daily.revenueByPaymentMethod(), series);
    }
}
