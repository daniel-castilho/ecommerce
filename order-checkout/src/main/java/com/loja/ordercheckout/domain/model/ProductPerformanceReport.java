package com.loja.ordercheckout.domain.model;

import java.util.List;

/**
 * Product performance report over all-time sales (admin reporting, backlog
 * S21). Right-sized to the current domain: the order model has no cost-price
 * input, so "profit margin" is not part of the report yet.
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
