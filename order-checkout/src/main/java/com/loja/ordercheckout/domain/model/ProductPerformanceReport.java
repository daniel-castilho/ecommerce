package com.loja.ordercheckout.domain.model;

import java.util.List;

/**
 * Product performance report over all-time sales (admin reporting, backlog
 * S21). Each row carries the gross profit margin derived from the product's
 * cost price; rows for products without a cost price or without sales report a
 * {@code null} margin.
 *
 * @param topSellers      top 10 products by units sold (units desc, name asc).
 * @param topByRevenue    top 10 products by revenue (revenue desc, name asc).
 * @param bottomPerformers bottom 10 products by units sold (units asc, revenue desc) — the underperformers.
 * @param unitsByCategory units sold grouped by category, ordered by units desc then name.
 */
public record ProductPerformanceReport(List<ProductPerformanceRow> topSellers,
                                       List<ProductPerformanceRow> topByRevenue,
                                       List<ProductPerformanceRow> bottomPerformers,
                                       List<CategoryUnits> unitsByCategory) { }
