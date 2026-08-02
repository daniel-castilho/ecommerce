package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.exception.InvalidOrderStateException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.PaymentInfo;
import com.loja.ordercheckout.domain.port.in.CustomerOrderHistoryUseCase;
import com.loja.ordercheckout.domain.port.out.NotificationPort;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;

/**
 * Serves the customer's order history and the cancel/refund flows. Depends only
 * on ports (DIP); ownership checks keep every customer restricted to their own
 * orders.
 */
@ApplicationScoped
public class OrderHistoryService implements CustomerOrderHistoryUseCase {

    private final OrderRepositoryPort orderRepository;
    private final NotificationPort notification;

    @Inject
    public OrderHistoryService(OrderRepositoryPort orderRepository, NotificationPort notification) {
        this.orderRepository = orderRepository;
        this.notification = notification;
    }

    @Transactional
    @Override
    public PageResult<Order> listByCustomer(String userId, int page, int pageSize) {
        return orderRepository.findByCustomerId(userId, page, pageSize);
    }

    @Transactional
    @Override
    public Optional<Order> findById(String orderId, String userId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.getUserId().equals(userId));
    }

    @Transactional
    @Override
    public Order cancel(String orderId, String userId) {
        Order order = requireOwnedOrder(orderId, userId);
        order.cancel();
        return orderRepository.save(order);
    }

    @Transactional
    @Override
    public Order requestRefund(String orderId, String userId, String reason) {
        Order order = requireOwnedOrder(orderId, userId);
        PaymentInfo payment = order.getPaymentInfo();
        if (payment == null || !payment.isCaptured()) {
            throw new InvalidOrderStateException("Order has no captured payment to refund");
        }
        if (payment.getRefundableAmount().getAmount().signum() <= 0) {
            throw new InvalidOrderStateException("Order has no balance left to refund");
        }
        order.requestRefund(payment.getRefundableAmount());
        Order saved = orderRepository.save(order);
        notification.notifyRefundRequested(saved, reason);
        return saved;
    }

    private Order requireOwnedOrder(String orderId, String userId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
    }
}
