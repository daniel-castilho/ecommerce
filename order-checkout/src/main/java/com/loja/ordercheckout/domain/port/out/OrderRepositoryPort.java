package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(String id);
    PageResult<Order> findByCustomerId(String customerId, int page, int pageSize);
    List<Order> findByStatus(OrderStatus status);

    /** Total number of persisted orders (admin metrics). */
    long countAll();
}
