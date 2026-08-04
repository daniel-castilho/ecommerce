package com.loja.admindashboard.application.service;

import com.loja.admindashboard.domain.port.in.OrderListUseCase;
import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application service for the admin order-management slice.
 */
@ApplicationScoped
public class OrderListService implements OrderListUseCase {

    private final OrderRepositoryPort orderRepository;

    @Inject
    public OrderListService(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public PageResult<Order> listOrders(int page, int pageSize) {
        return orderRepository.findAll(page, pageSize);
    }

    @Override
    public PageResult<Order> listOrders(OrderStatus status, int page, int pageSize) {
        return orderRepository.findByStatus(status, page, pageSize);
    }
}
