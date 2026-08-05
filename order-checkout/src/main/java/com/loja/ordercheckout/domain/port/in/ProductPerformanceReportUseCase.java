package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.domain.model.ProductPerformanceReport;

/**
 * Input port (admin-only): product performance report over all-time sales
 * (backlog S21).
 */
public interface ProductPerformanceReportUseCase {

    /**
     * All-time product sales report. Top sellers (by units), top by revenue,
     * bottom performers (by units) and units sold by category.
     *
     * @param categoryId optional category filter ({@code null} = all categories).
     */
    ProductPerformanceReport productPerformanceReport(Long categoryId);
}
