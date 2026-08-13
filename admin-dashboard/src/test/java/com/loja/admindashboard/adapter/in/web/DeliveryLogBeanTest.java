package com.loja.admindashboard.adapter.in.web;

import com.loja.ordercheckout.domain.model.NotificationChannel;
import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.port.in.NotificationDeliveryManagementUseCase;
import jakarta.faces.application.Application;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ResponseStream;
import jakarta.faces.context.ResponseWriter;
import jakarta.faces.lifecycle.Lifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryLogBeanTest {

    private NotificationDeliveryManagementUseCase useCase;
    private DeliveryLogBean bean;

    @BeforeEach
    void setUp() {
        FacesContextAccessor.setCurrent(new FacesContextAccessor());
        useCase = mock(NotificationDeliveryManagementUseCase.class);
        bean = new DeliveryLogBean();
        bean.setDeliveryManagement(useCase);
    }

    @AfterEach
    void tearDown() {
        FacesContextAccessor.setCurrent(null);
    }

    private NotificationDelivery delivery(String key, NotificationDeliveryStatus status) {
        return NotificationDelivery.reconstitute("id-" + key, "ORDER_CONFIRMED", "o-1",
                NotificationChannel.EMAIL, key, status, 3, "boom",
                "buyer@example.com", "Order o-1 confirmed", "Body", "<html>Body</html>", null,
                java.time.Instant.now(), java.time.Instant.now());
    }

    @Test
    void refresh_loadsDeliveriesForCurrentFilter() {
        when(useCase.listDeliveries(null)).thenReturn(List.of(delivery("ORDER_CONFIRMED:a", NotificationDeliveryStatus.PENDING)));

        bean.refresh();

        assertThat(bean.getDeliveries()).extracting(NotificationDelivery::getIdempotencyKey)
                .containsExactly("ORDER_CONFIRMED:a");
    }

    @Test
    void refresh_usesSelectedStatusFilter() {
        when(useCase.listDeliveries(NotificationDeliveryStatus.EXHAUSTED))
                .thenReturn(List.of(delivery("ORDER_CONFIRMED:a", NotificationDeliveryStatus.EXHAUSTED)));

        bean.setStatusFilter(NotificationDeliveryStatus.EXHAUSTED);
        bean.refresh();

        verify(useCase).listDeliveries(NotificationDeliveryStatus.EXHAUSTED);
    }

    @Test
    void resend_requeuesDeliveryAndRefreshes() {
        NotificationDelivery exhausted = delivery("ORDER_CONFIRMED:a", NotificationDeliveryStatus.EXHAUSTED);
        when(useCase.listDeliveries(null)).thenReturn(List.of(exhausted));
        when(useCase.resend("ORDER_CONFIRMED:a")).thenReturn(true);

        bean.refresh();
        bean.resend(exhausted);

        verify(useCase).resend("ORDER_CONFIRMED:a");
        verify(useCase, org.mockito.Mockito.times(2)).listDeliveries(null);
    }

    @Test
    void errorPreview_truncatesLongErrors() {
        NotificationDelivery longError = NotificationDelivery.reconstitute("id-b", "ORDER_CONFIRMED", "o-1",
                NotificationChannel.EMAIL, "ORDER_CONFIRMED:b", NotificationDeliveryStatus.FAILED, 1,
                "x".repeat(200), "buyer@example.com", "Subject", "Body", null, null,
                java.time.Instant.now(), java.time.Instant.now());

        assertThat(bean.errorPreview(longError)).endsWith("…");
        assertThat(bean.isErrorTruncated(longError)).isTrue();
        assertThat(bean.fullError(longError)).hasSize(200);
    }

    static class FacesContextAccessor extends FacesContext {
        static void setCurrent(FacesContext context) {
            setCurrentInstance(context);
        }

        @Override
        public Application getApplication() { return null; }
        @Override
        public ExternalContext getExternalContext() { return null; }
        @Override
        public void addMessage(String clientId, FacesMessage message) {}
        @Override
        public void release() {}
        @Override
        public ResponseStream getResponseStream() { return null; }
        @Override
        public void setResponseStream(ResponseStream responseStream) {}
        @Override
        public ResponseWriter getResponseWriter() { return null; }
        @Override
        public void setResponseWriter(ResponseWriter responseWriter) {}
        @Override
        public UIViewRoot getViewRoot() { return null; }
        @Override
        public void setViewRoot(UIViewRoot root) {}
        @Override
        public void renderResponse() {}
        @Override
        public Lifecycle getLifecycle() { return null; }
        @Override
        public Iterator<String> getClientIdsWithMessages() { return null; }
        @Override
        public FacesMessage.Severity getMaximumSeverity() { return null; }
        @Override
        public Iterator<FacesMessage> getMessages() { return null; }
        @Override
        public Iterator<FacesMessage> getMessages(String clientId) { return null; }
        @Override
        public jakarta.faces.render.RenderKit getRenderKit() { return null; }
        @Override
        public boolean getRenderResponse() { return false; }
        @Override
        public boolean getResponseComplete() { return false; }
        @Override
        public void responseComplete() {}
    }
}
