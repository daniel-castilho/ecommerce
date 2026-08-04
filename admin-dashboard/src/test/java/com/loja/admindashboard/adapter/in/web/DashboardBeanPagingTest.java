package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.admindashboard.domain.port.in.DashboardMetricsUseCase;
import com.loja.admindashboard.domain.port.in.OrderListUseCase;
import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;

class DashboardBeanPagingTest {

    private DashboardBean bean;
    private OrderListUseCase orderListUseCase;

    @BeforeEach
    void setUp() {
        bean = new DashboardBean();
        orderListUseCase = mock(OrderListUseCase.class);
        DashboardMetricsUseCase dashboardMetricsUseCase = mock(DashboardMetricsUseCase.class);
        bean.setOrderListUseCase(orderListUseCase);
        bean.setDashboardMetricsUseCase(dashboardMetricsUseCase);
        when(dashboardMetricsUseCase.getSummary()).thenReturn(null);
    }

    @Test
    void filterOrders_resetsToFirstPageAndUsesSelectedStatus() {
        PageResult<Order> page = new PageResult<>(List.of(), 1L, 0, 5);
        when(orderListUseCase.listOrders(OrderStatus.PROCESSING, 0, 5)).thenReturn(page);

        bean.setSelectedStatus(OrderStatus.PROCESSING);
        bean.filterOrders();

        assertThat(bean.getOrderPage()).isEqualTo(page);
        assertThat(bean.getPage()).isZero();
        verify(orderListUseCase).listOrders(OrderStatus.PROCESSING, 0, 5);
    }
}
