package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.loja.admindashboard.application.dto.OrderDetailsDTO;
import com.loja.admindashboard.domain.port.in.GetOrderDetailsUseCase;

class AdminOrderDetailBeanTest {

    @Test
    void loadOrder_populatesSelectedOrderFromUseCase() {
        GetOrderDetailsUseCase getOrderDetailsUseCase = mock(GetOrderDetailsUseCase.class);
        AdminOrderDetailBean bean = new AdminOrderDetailBean();
        bean.setGetOrderDetailsUseCase(getOrderDetailsUseCase);

        OrderDetailsDTO order = new OrderDetailsDTO(
                "o-1",
                null,
                "customer@example.com",
                null,
                null,
                null,
                null,
                null,
                null);
        when(getOrderDetailsUseCase.findById("o-1")).thenReturn(Optional.of(order));

        bean.loadOrder("o-1");

        assertThat(bean.getSelectedOrder()).isEqualTo(order);
        verify(getOrderDetailsUseCase).findById("o-1");
    }
}
