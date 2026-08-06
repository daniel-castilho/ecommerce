package com.loja.ordercheckout.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.loja.ordercheckout.domain.model.CustomerGrowthPoint;
import com.loja.ordercheckout.domain.model.CustomerInsightsReport;
import com.loja.ordercheckout.domain.port.in.CustomerInsightsReportUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.shared.domain.Money;
import com.loja.useraccount.domain.model.UserGrowthPoint;
import com.loja.useraccount.domain.port.out.UserRepositoryPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Customer insights report (admin reporting, backlog S22). Depends only on
 * repository ports (DIP): user data comes through the user-account port, order
 * data through the order-checkout port. All metric definitions (repeat rate,
 * churn rate, average LTV) live here so the admin-dashboard module stays free
 * of business rules.
 */
@ApplicationScoped
public class CustomerInsightsReportService implements CustomerInsightsReportUseCase {

    /** An account with no activity for this long counts as churned. */
    private static final Duration CHURN_INACTIVITY = Duration.ofDays(90);

    private final OrderRepositoryPort orderRepository;
    private final UserRepositoryPort userRepository;

    @Inject
    public CustomerInsightsReportService(OrderRepositoryPort orderRepository,
                                         UserRepositoryPort userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Override
    public CustomerInsightsReport customerInsightsReport(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Report range must not be null");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Report 'from' must not be after 'to'");
        }

        long totalCustomers = userRepository.count();

        List<CustomerGrowthPoint> series = userRepository.userGrowthSeries(from, to).stream()
                .map(point -> new CustomerGrowthPoint(point.date(), point.count()))
                .toList();
        long newCustomers = series.stream()
                .mapToLong(CustomerGrowthPoint::count)
                .sum();

        long repeatCustomers = orderRepository.repeatCustomerCount();
        double repeatCustomerRate = percentage(repeatCustomers, totalCustomers);

        Money totalRevenue = orderRepository.totalCustomerRevenue();
        Money averageLtv = totalCustomers == 0
                ? Money.zero()
                : new Money(totalRevenue.getAmount()
                        .divide(BigDecimal.valueOf(totalCustomers), 2, RoundingMode.HALF_UP));

        Instant churnCutoff = Instant.now().minus(CHURN_INACTIVITY);
        double churnRate = percentage(userRepository.countInactiveSince(churnCutoff), totalCustomers);

        return new CustomerInsightsReport(totalCustomers, newCustomers, repeatCustomerRate,
                averageLtv, churnRate, series);
    }

    private static double percentage(long part, long total) {
        return total == 0 ? 0.0 : part * 100.0 / total;
    }
}
