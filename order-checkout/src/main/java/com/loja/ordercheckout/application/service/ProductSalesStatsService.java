package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.model.ProductSalesAggregate;
import com.loja.ordercheckout.domain.port.in.ProductSalesStatsUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Thin facade over {@link OrderRepositoryPort#productSales()} so the
 * admin-dashboard product list can consume sales stats through an input port
 * instead of reaching into an outbound port (backlog S10/S21 debt).
 */
@ApplicationScoped
public class ProductSalesStatsService implements ProductSalesStatsUseCase {

    private final OrderRepositoryPort orderRepository;

    @Inject
    public ProductSalesStatsService(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Map<String, ProductSalesAggregate> salesByProductId() {
        return orderRepository.productSales().stream()
                .collect(Collectors.toMap(ProductSalesAggregate::productId, Function.identity()));
    }
}
