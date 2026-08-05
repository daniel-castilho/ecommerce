package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;

/**
 * All-time sales aggregate for a single product, computed from order lines
 * (admin reporting, backlog S21). Excludes CANCELLED and REFUNDED orders,
 * mirroring the revenue report.
 */
public record ProductSalesAggregate(String productId, long unitsSold, Money revenue) { }
