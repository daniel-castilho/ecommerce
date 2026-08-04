package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.in.RefundManagementUseCase;
import com.loja.shared.domain.Money;

import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.Application;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

class RefundDetailBeanTest {

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

    private RefundManagementUseCase useCase;
    private RefundDetailBean bean;

    @BeforeEach
    void setUp() {
        FacesContextAccessor.setCurrent(new FacesContextAccessor());
        useCase = mock(RefundManagementUseCase.class);
        bean = new RefundDetailBean();
        bean.setRefundManagementUseCase(useCase);
    }

    @AfterEach
    void tearDown() {
        FacesContextAccessor.setCurrent(null);
    }

    @Test
    void refundDetailBean_isExposedAsViewScopedAdminBean() {
        Class<RefundDetailBean> beanClass = RefundDetailBean.class;

        Named named = beanClass.getAnnotation(Named.class);
        ViewScoped viewScoped = beanClass.getAnnotation(ViewScoped.class);
        RolesAllowed rolesAllowed = beanClass.getAnnotation(RolesAllowed.class);

        assertThat(named).isNotNull();
        assertThat(named.value()).isEqualTo("refundDetailBean");
        assertThat(viewScoped).isNotNull();
        assertThat(rolesAllowed).isNotNull();
        assertThat(rolesAllowed.value()).containsExactly("ADMIN");
    }

    @Test
    void loadRefund_populatesSelectedRefund() {
        RefundRequest request = pendingRefund();
        when(useCase.findRefundById("r-1")).thenReturn(Optional.of(request));

        bean.loadRefund("r-1");

        assertThat(bean.getSelectedRefund()).isEqualTo(request);
        assertThat(bean.isPending()).isTrue();
    }

    @Test
    void loadRefund_withUnknownId_leavesNothingSelected() {
        when(useCase.findRefundById("r-1")).thenReturn(Optional.empty());

        bean.loadRefund("r-1");

        assertThat(bean.getSelectedRefund()).isNull();
        assertThat(bean.isPending()).isFalse();
    }

    @Test
    void approve_delegatesToUseCaseAndReloads() {
        RefundRequest request = pendingRefund();
        when(useCase.findRefundById("r-1")).thenReturn(Optional.of(request));
        bean.loadRefund("r-1");

        bean.approve();

        verify(useCase).approveRefund("r-1");
    }

    @Test
    void approve_whenGatewayFails_doesNotPropagate() {
        RefundRequest request = pendingRefund();
        when(useCase.findRefundById("r-1")).thenReturn(Optional.of(request));
        org.mockito.Mockito.doThrow(new RuntimeException("payment failed"))
                .when(useCase).approveRefund("r-1");
        bean.loadRefund("r-1");

        bean.approve();

        verify(useCase).approveRefund("r-1");
        assertThat(bean.getSelectedRefund()).isNotNull();
    }

    @Test
    void reject_requiresReason() {
        RefundRequest request = pendingRefund();
        when(useCase.findRefundById("r-1")).thenReturn(Optional.of(request));
        bean.loadRefund("r-1");
        bean.setRejectionReason("   ");

        bean.reject();

        verify(useCase, never()).rejectRefund("r-1", "   ");
    }

    @Test
    void reject_delegatesToUseCaseAndReloads() {
        RefundRequest request = pendingRefund();
        when(useCase.findRefundById("r-1")).thenReturn(Optional.of(request));
        bean.loadRefund("r-1");
        bean.setRejectionReason("Policy violation");

        bean.reject();

        verify(useCase).rejectRefund("r-1", "Policy violation");
    }

    @Test
    void isPending_reflectsCurrentStatus() {
        RefundRequest processed = pendingRefund();
        processed.approve();
        processed.markAsProcessed();
        when(useCase.findRefundById("r-2")).thenReturn(Optional.of(processed));

        bean.loadRefund("r-2");

        assertThat(bean.getSelectedRefund().getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(bean.isPending()).isFalse();
    }

    private static RefundRequest pendingRefund() {
        return RefundRequest.reconstitute("r-1", "o-1", new Money(new BigDecimal("50.00")),
                "Damaged item", RefundStatus.PENDING, null, java.time.Instant.now(), null);
    }
}
