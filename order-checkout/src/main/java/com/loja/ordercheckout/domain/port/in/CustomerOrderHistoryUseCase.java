package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import java.util.Optional;

/**
 * Inbound use case for the customer's order history: paginated listing of the
 * caller's own orders plus cancel and refund operations. All order-scoped
 * methods take the caller's {@code userId} so one customer can never read or
 * mutate another customer's order.
 */
public interface CustomerOrderHistoryUseCase {

    /** Paginated orders for the customer, newest first. */
    PageResult<Order> listByCustomer(String userId, int page, int pageSize);

    /** Order detail, present only if the order belongs to the given user. */
    Optional<Order> findById(String orderId, String userId);

    /**
     * Cancels an order owned by the user (PENDING/CONFIRMED per the UI rules;
     * the domain state machine still guards the transition).
     *
     * @return the persisted cancelled order
     * @throws IllegalArgumentException if the order is missing or not owned
     */
    Order cancel(String orderId, String userId);

    /**
     * Refunds the full remaining captured balance of an owned order and notifies
     * the customer.
     *
     * @param reason customer-supplied refund reason (notification payload)
     * @return the persisted order, now in {@code REFUNDED} status
     * @throws IllegalArgumentException if the order is missing or not owned
     */
    Order requestRefund(String orderId, String userId, String reason);
}
