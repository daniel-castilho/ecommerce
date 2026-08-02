package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.application.dto.CheckoutCommand;
import com.loja.ordercheckout.domain.model.Order;

/**
 * Inbound use case: place an order from a cart, quoting shipping, processing the
 * payment and decrementing inventory atomically. A {@code requestId} makes the
 * operation idempotent: replaying the same command returns the already-created order.
 */
public interface CreateOrderFromCartUseCase {

    /**
     * @param command all inputs required to place the order
     * @return the persisted order — {@code CONFIRMED} on success, or {@code PENDING}
     *         when the payment capture failed (no notification is sent in that case)
     */
    Order checkout(CheckoutCommand command);
}
