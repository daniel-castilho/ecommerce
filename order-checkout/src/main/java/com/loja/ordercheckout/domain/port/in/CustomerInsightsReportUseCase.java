package com.loja.ordercheckout.domain.port.in;

import java.time.Instant;

import com.loja.ordercheckout.domain.model.CustomerInsightsReport;

/**
 * Customer base report over a date range (admin reporting, backlog S22).
 * Composes user-account and order-checkout repository ports; the admin-dashboard
 * module only calls this port and renders the result.
 */
public interface CustomerInsightsReportUseCase {

    /**
     * Builds the customer insights report. {@code from} and {@code to} bound the
     * "new customers" period (new accounts and the daily series); total customers,
     * repeat rate, average lifetime value and churn rate are all-time figures.
     *
     * @throws IllegalArgumentException if the range is invalid (null or reversed).
     */
    CustomerInsightsReport customerInsightsReport(Instant from, Instant to);
}
