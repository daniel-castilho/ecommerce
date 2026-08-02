package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.domain.model.Order;
import java.util.Optional;

public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(String id);
}
