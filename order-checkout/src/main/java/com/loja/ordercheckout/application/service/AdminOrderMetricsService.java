package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.port.in.AdminOrderMetricsUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Admin-facing aggregate metrics over persisted orders. Depends only on the
 * repository port (DIP); used by the admin-dashboard module.
 */
@ApplicationScoped
public class AdminOrderMetricsService implements AdminOrderMetricsUseCase {

    private final OrderRepositoryPort orderRepository;

    @Inject
    public AdminOrderMetricsService(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public long countAllOrders() {
        return orderRepository.countAll();
    }
}
