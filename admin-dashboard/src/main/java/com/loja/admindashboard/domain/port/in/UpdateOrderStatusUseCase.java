package com.loja.admindashboard.domain.port.in;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;

/**
 * Admin use case for updating the lifecycle state of an order.
 */
public interface UpdateOrderStatusUseCase {
    Order updateStatus(String orderId, OrderStatus status, String trackingNumber);
}
