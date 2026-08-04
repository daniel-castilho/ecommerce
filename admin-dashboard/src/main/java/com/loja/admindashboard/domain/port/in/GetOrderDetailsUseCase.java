package com.loja.admindashboard.domain.port.in;

import java.util.Optional;

import com.loja.admindashboard.application.dto.OrderDetailsDTO;

/**
 * Read-only use case for the admin order-detail flow.
 */
public interface GetOrderDetailsUseCase {
    Optional<OrderDetailsDTO> findById(String orderId);
}
