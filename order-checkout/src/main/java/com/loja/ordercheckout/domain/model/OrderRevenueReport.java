package com.loja.ordercheckout.domain.model;

import java.util.List;
import java.util.Map;

import com.loja.shared.domain.Money;

/**
 * Revenue report over a date range (admin reporting, backlog S20). Excludes
 * CANCELLED and REFUNDED orders, mirroring {@code revenueSince}. The time series
 * is always daily at the repository boundary; the report use case rolls it up to
 * the requested granularity.
 *
 * <p>Right-sized: the order model has no tax column and order lines carry no
 * category, so "total tax" and "revenue by category" are not part of the report
 * yet (see admin-dashboard backlog S20 notes).
 *
 * @param revenueByPaymentMethod revenue grouped by payment method label, ordered by method.
 * @param dailySeries            revenue by local date, ordered by date.
 */
public record OrderRevenueReport(Money totalRevenue, Money itemsRevenue, Money shippingRevenue,
                                 long orderCount, Money averageOrderValue,
                                 Map<String, Money> revenueByPaymentMethod,
                                 List<RevenuePoint> dailySeries) { }
