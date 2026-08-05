package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;

/**
 * One row of the product performance report (backlog S21): catalog identity
 * (SKU/name) plus all-time units sold and revenue from non-excluded orders.
 * A product with no sales is present with zero units and zero revenue so the
 * "bottom performers" list includes products that never sold.
 */
public record ProductPerformanceRow(String sku, String name, long unitsSold, Money revenue) { }
