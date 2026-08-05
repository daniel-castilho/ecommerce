package com.loja.ordercheckout.domain.port.in;

import java.time.Instant;

import com.loja.ordercheckout.domain.model.OrderRevenueReport;
import com.loja.ordercheckout.domain.model.ReportGranularity;

/** Input port (admin-only): revenue report over a date range. */
public interface RevenueReportUseCase {

    /**
     * Revenue report for orders created in {@code [from, to)}, excluding CANCELLED
     * and REFUNDED orders.
     *
     * @throws IllegalArgumentException if the range is null or {@code from} is after {@code to}.
     */
    OrderRevenueReport revenueReport(Instant from, Instant to, ReportGranularity granularity);
}
