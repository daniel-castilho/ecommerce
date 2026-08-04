package com.loja.admindashboard.application.dto;

import com.loja.ordercheckout.domain.model.OrderMetrics;

/**
 * Immutable snapshot of the dashboard KPIs. Order KPIs are carried as the
 * order-checkout module's own value object; the dashboard only aggregates
 * results from each module's input ports and never re-implements their logic.
 */
public record DashboardSummaryDTO(long totalUsers, long totalProducts, long totalOrders,
                                  long newCustomersToday, long newCustomersThisMonth,
                                  OrderMetrics orderMetrics) { }
