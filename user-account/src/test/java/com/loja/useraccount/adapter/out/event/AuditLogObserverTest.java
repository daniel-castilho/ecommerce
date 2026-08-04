package com.loja.useraccount.adapter.out.event;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.loja.shared.event.ProductArchivedEvent;
import com.loja.shared.event.RefundProcessedEvent;
import com.loja.shared.event.RefundRejectedEvent;
import com.loja.useraccount.domain.event.RoleAssignedEvent;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.port.out.AuditLogPort;
import com.loja.useraccount.domain.port.out.SessionPort;

import jakarta.servlet.http.HttpServletRequest;

class AuditLogObserverTest {

    private final AuditLogPort auditLog = mock(AuditLogPort.class);

    private final SessionPort session = mock(SessionPort.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    private AuditLogObserver observer;

    @BeforeEach
    void setUp() throws Exception {
        observer = new AuditLogObserver();
        injectField("auditLog", auditLog);
        injectField("session", session);
        injectField("request", request);
    }

    @Test
    void shouldLogRoleAssignedEvent() {
        observer.onRoleAssigned(new RoleAssignedEvent("user-1", Role.ADMIN, "admin-9"));

        verify(auditLog).logEvent("user-1", "admin-9", "ROLE_ASSIGNED", null, null, "Role assigned: ADMIN");
    }

    @Test
    void shouldLogRefundProcessed_withActor() {
        when(session.getCurrentUser()).thenReturn(java.util.Optional.of(new com.loja.useraccount.domain.model.User("a1", null, null, null)));

        observer.onRefundProcessed(new RefundProcessedEvent("r-1", "o-1", java.time.Instant.now()));

        verify(auditLog).logEvent("a1", null, "REFUND_PROCESSED", null, null,
                "Refund processed: id=r-1, order=o-1");
    }

    @Test
    void shouldLogRefundRejected_withActorAndReason() {
        when(session.getCurrentUser()).thenReturn(java.util.Optional.of(new com.loja.useraccount.domain.model.User("a1", null, null, null)));

        observer.onRefundRejected(new RefundRejectedEvent("r-1", "o-1", "Policy violation", java.time.Instant.now()));

        verify(auditLog).logEvent("a1", null, "REFUND_REJECTED", null, null,
                "Refund rejected: id=r-1, order=o-1, reason=Policy violation");
    }

    private void injectField(String fieldName, Object value) throws Exception {
        Field field = AuditLogObserver.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(observer, value);
    }

    @Test
    void shouldLogProductArchived_withIpAndUserAgent_andSkuName() throws Exception {
        when(session.getCurrentUser()).thenReturn(java.util.Optional.of(new com.loja.useraccount.domain.model.User("u1", null, null, null)));
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");
        when(request.getHeader("User-Agent")).thenReturn("JUnit-Agent/1.0");

        ProductArchivedEvent evt = new ProductArchivedEvent("p1", "SKU-X", "Product X", java.time.Instant.now());
        observer.onProductArchived(evt);

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLog, times(1)).logEvent(eq("u1"), (String) eq(null), eq("PRODUCT_ARCHIVED"), eq("203.0.113.5"), eq("JUnit-Agent/1.0"), detailsCaptor.capture());
        String details = detailsCaptor.getValue();
        assert details.contains("p1");
        assert details.contains("SKU-X");
        assert details.contains("Product X");
    }
}
