package com.loja.admindashboard.application.service;

import com.loja.admindashboard.domain.port.in.UpdateOrderStatusUseCase;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.NotificationPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application service for admin-driven order status changes.
 */
@ApplicationScoped
public class UpdateOrderStatusService implements UpdateOrderStatusUseCase {

    private final OrderRepositoryPort orderRepository;
    private final NotificationPort notificationPort;

    @Inject
    public UpdateOrderStatusService(OrderRepositoryPort orderRepository, NotificationPort notificationPort) {
        this.orderRepository = orderRepository;
        this.notificationPort = notificationPort;
    }

    @Override
    public Order updateStatus(String orderId, OrderStatus status, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (status == OrderStatus.SHIPPED) {
            order.ship(trackingNumber == null ? "" : trackingNumber);
        } else if (status == OrderStatus.PROCESSING) {
            order.process();
        } else if (status == OrderStatus.DELIVERED) {
            order.deliver();
        } else if (status == OrderStatus.CANCELLED) {
            order.cancel();
        } else if (status == OrderStatus.CONFIRMED) {
            order.confirm();
        } else if (status == OrderStatus.REFUNDED) {
            order.requestRefund(null);
        } else {
            order.cancel();
        }

        Order saved = orderRepository.save(order);

        if (status == OrderStatus.SHIPPED) {
            notificationPort.notifyOrderShipped(saved, trackingNumber == null ? "" : trackingNumber);
        } else if (status == OrderStatus.CONFIRMED) {
            notificationPort.notifyOrderConfirmed(saved);
        }

        return saved;
    }
}
