package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.domain.model.ProductSalesAggregate;
import java.util.Map;

/**
 * All-time sales aggregates keyed by product id (admin product list, backlog
 * S10/S21 debt). Lets the admin-dashboard list merge units sold and revenue into
 * each product row without reaching into an order-checkout outbound port; profit
 * margin itself is derived from the product's cost price on the catalog side.
 */
public interface ProductSalesStatsUseCase {

    Map<String, ProductSalesAggregate> salesByProductId();
}
