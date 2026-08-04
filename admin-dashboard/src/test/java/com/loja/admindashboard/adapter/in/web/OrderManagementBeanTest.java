package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.loja.admindashboard.domain.port.in.OrderListUseCase;
import com.loja.admindashboard.domain.port.in.UpdateOrderStatusUseCase;
import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;

class OrderManagementBeanTest {

    @Test
    void filterOrders_usesSelectedStatusAndRefreshesPage() {
        OrderListUseCase orderListUseCase = mock(OrderListUseCase.class);
        UpdateOrderStatusUseCase updateOrderStatusUseCase = mock(UpdateOrderStatusUseCase.class);
        OrderManagementBean bean = new OrderManagementBean();
        bean.setOrderListUseCase(orderListUseCase);
        bean.setUpdateOrderStatusUseCase(updateOrderStatusUseCase);

        PageResult<Order> expected = new PageResult<>(List.of(new Order("o-1", "u-1", "customer@example.com")),
                1L, 0, 10);
        when(orderListUseCase.listOrders(OrderStatus.PROCESSING, 0, 10)).thenReturn(expected);

        bean.setSelectedStatus(OrderStatus.PROCESSING);
        bean.filterOrders();

        assertThat(bean.getOrders()).hasSize(1);
        verify(orderListUseCase).listOrders(OrderStatus.PROCESSING, 0, 10);
    }
}
