package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.loja.admindashboard.domain.port.in.DashboardMetricsUseCase;
import com.loja.admindashboard.domain.port.in.OrderListUseCase;
import com.loja.admindashboard.domain.port.in.UpdateOrderStatusUseCase;
import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;

import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.Application;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

class DashboardBeanTest {

    static final class FacesContextAccessor extends FacesContext {
        static void setCurrent(FacesContext context) { setCurrentInstance(context); }
        @Override public Application getApplication() { return null; }
        @Override public ExternalContext getExternalContext() { return null; }
        @Override public void addMessage(String clientId, FacesMessage message) {}
        @Override public void release() {}
        @Override public jakarta.faces.context.ResponseStream getResponseStream() { return null; }
        @Override public void setResponseStream(jakarta.faces.context.ResponseStream responseStream) {}
        @Override public jakarta.faces.context.ResponseWriter getResponseWriter() { return null; }
        @Override public void setResponseWriter(jakarta.faces.context.ResponseWriter responseWriter) {}
        @Override public jakarta.faces.component.UIViewRoot getViewRoot() { return null; }
        @Override public void setViewRoot(jakarta.faces.component.UIViewRoot root) {}
        @Override public void renderResponse() {}
        @Override public jakarta.faces.lifecycle.Lifecycle getLifecycle() { return null; }
        @Override public java.util.Iterator<String> getClientIdsWithMessages() { return null; }
        @Override public FacesMessage.Severity getMaximumSeverity() { return null; }
        @Override public java.util.Iterator<FacesMessage> getMessages() { return null; }
        @Override public java.util.Iterator<FacesMessage> getMessages(String clientId) { return null; }
        @Override public jakarta.faces.render.RenderKit getRenderKit() { return null; }
        @Override public boolean getRenderResponse() { return false; }
        @Override public boolean getResponseComplete() { return false; }
        @Override public void responseComplete() {}
    }

    @BeforeEach
    void setUp() {
        FacesContextAccessor.setCurrent(new FacesContextAccessor());
    }

    @AfterEach
    void tearDown() {
        FacesContextAccessor.setCurrent(null);
    }

    @Test
    void dashboardBean_isExposedAsViewScopedAdminBean() {
        Class<DashboardBean> beanClass = DashboardBean.class;

        Named named = beanClass.getAnnotation(Named.class);
        ViewScoped viewScoped = beanClass.getAnnotation(ViewScoped.class);
        RolesAllowed rolesAllowed = beanClass.getAnnotation(RolesAllowed.class);

        assertThat(named).isNotNull();
        assertThat(named.value()).isEqualTo("dashboardBean");
        assertThat(viewScoped).isNotNull();
        assertThat(rolesAllowed).isNotNull();
        assertThat(rolesAllowed.value()).containsExactly("ADMIN");
    }

    @Test
    void updateSelectedOrderStatus_callsUseCaseAndRefreshesOrders() {
        DashboardMetricsUseCase dashboardMetricsUseCase = mock(DashboardMetricsUseCase.class);
        OrderListUseCase orderListUseCase = mock(OrderListUseCase.class);
        UpdateOrderStatusUseCase updateOrderStatusUseCase = mock(UpdateOrderStatusUseCase.class);

        DashboardBean bean = new DashboardBean();
        bean.setDashboardMetricsUseCase(dashboardMetricsUseCase);
        bean.setOrderListUseCase(orderListUseCase);
        bean.setUpdateOrderStatusUseCase(updateOrderStatusUseCase);

        PageResult<Order> expected = new PageResult<>(List.of(new Order("o-1", "u-1", "customer@example.com")),
                1L, 0, 5);
        when(orderListUseCase.listOrders(0, 5)).thenReturn(expected);
        when(updateOrderStatusUseCase.updateStatus("o-1", OrderStatus.PROCESSING, null))
                .thenReturn(new Order("o-1", "u-1", "customer@example.com"));

        bean.setSelectedOrderId("o-1");
        bean.setStatusToApply(OrderStatus.PROCESSING);
        bean.updateSelectedOrderStatus();

        assertThat(bean.getRecentOrders()).hasSize(1);
        verify(updateOrderStatusUseCase).updateStatus("o-1", OrderStatus.PROCESSING, null);
        verify(orderListUseCase).listOrders(0, 5);
    }

    @Test
    void updateSelectedOrderStatus_passesTrackingNumberWhenShipping() {
        DashboardMetricsUseCase dashboardMetricsUseCase = mock(DashboardMetricsUseCase.class);
        OrderListUseCase orderListUseCase = mock(OrderListUseCase.class);
        UpdateOrderStatusUseCase updateOrderStatusUseCase = mock(UpdateOrderStatusUseCase.class);

        DashboardBean bean = new DashboardBean();
        bean.setDashboardMetricsUseCase(dashboardMetricsUseCase);
        bean.setOrderListUseCase(orderListUseCase);
        bean.setUpdateOrderStatusUseCase(updateOrderStatusUseCase);

        PageResult<Order> expected = new PageResult<>(List.of(new Order("o-1", "u-1", "customer@example.com")),
                1L, 0, 5);
        when(orderListUseCase.listOrders(0, 5)).thenReturn(expected);

        bean.setSelectedOrderId("o-1");
        bean.setStatusToApply(OrderStatus.SHIPPED);
        bean.setTrackingNumber("TRACK-123");
        bean.updateSelectedOrderStatus();

        verify(updateOrderStatusUseCase).updateStatus("o-1", OrderStatus.SHIPPED, "TRACK-123");
    }
}
