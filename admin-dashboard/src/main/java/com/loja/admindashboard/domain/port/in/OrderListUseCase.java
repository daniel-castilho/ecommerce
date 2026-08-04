package com.loja.admindashboard.domain.port.in;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;

/**
 * Read-only use case for the admin order list flow.
 */
public interface OrderListUseCase {
    PageResult<Order> listOrders(int page, int pageSize);

    default PageResult<Order> listOrders(OrderStatus status, int page, int pageSize) {
        return listOrders(page, pageSize);
    }
}
