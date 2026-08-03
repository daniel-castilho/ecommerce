package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;

import java.util.Map;

/**
 * Immutable snapshot of the admin dashboard's order KPIs. Lives in the domain
 * model so that input ports stay interfaces and the value object can be shared
 * with the admin-dashboard composition module.
 */
public record OrderMetrics(Money revenueToday, Money revenueThisMonth,
                           long ordersToday, long ordersThisMonth,
                           Map<OrderStatus, Long> ordersByStatus,
                           Money averageOrderValueThisMonth) { }
