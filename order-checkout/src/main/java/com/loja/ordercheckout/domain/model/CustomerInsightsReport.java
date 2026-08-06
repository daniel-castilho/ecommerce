package com.loja.ordercheckout.domain.model;

import java.util.List;

import com.loja.shared.domain.Money;

/**
 * Customer base report (admin reporting, backlog S22). Aggregates user-account
 * and order-checkout data through their repository ports; all percentages are
 * 0-100 and all metrics are computed over the whole customer base, so the
 * repeat rate and churn rate denominators match.
 *
 * @param totalCustomers     registered accounts (all-time).
 * @param newCustomers       accounts registered in the selected period.
 * @param repeatCustomerRate customers with more than one non-cancelled/non-refunded order
 *                           divided by total customers, in percent.
 * @param averageLtv         total non-cancelled/non-refunded order revenue divided by total
 *                           customers (all-time average lifetime value).
 * @param churnRate          customers inactive for 90+ days divided by total customers, in percent.
 * @param newCustomersSeries new accounts per day within the selected period.
 */
public record CustomerInsightsReport(
        long totalCustomers,
        long newCustomers,
        double repeatCustomerRate,
        Money averageLtv,
        double churnRate,
        List<CustomerGrowthPoint> newCustomersSeries) {
}
