package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;
import java.math.BigDecimal;

/**
 * One row of the product performance report (backlog S21): catalog identity
 * (SKU/name) plus all-time units sold, revenue and gross profit margin from
 * non-excluded orders. A product with no sales is present with zero units and
 * zero revenue so the "bottom performers" list includes products that never
 * sold.
 *
 * @param profitMargin gross profit margin percentage
 *                     {@code (revenue - costPrice * unitsSold) / revenue * 100},
 *                     {@code null} when no cost price is recorded or there are no
 *                     sales to derive a margin from.
 */
public record ProductPerformanceRow(String sku, String name, long unitsSold, Money revenue,
                                    BigDecimal profitMargin) { }
