package com.loja.useraccount.adapter.out.event;

import com.loja.useraccount.domain.event.RoleAssignedEvent;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.port.out.AuditLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogObserverTest {

    private final AuditLogPort auditLog = mock(AuditLogPort.class);

    private AuditLogObserver observer;

    @BeforeEach
    void setUp() throws Exception {
        observer = new AuditLogObserver();
        injectField("auditLog", auditLog);
    }

    @Test
    void shouldLogRoleAssignedEvent() {
        observer.onRoleAssigned(new RoleAssignedEvent("user-1", Role.ADMIN, "admin-9"));

        verify(auditLog).logEvent("user-1", "admin-9", "ROLE_ASSIGNED", null, null, "Role assigned: ADMIN");
    }

    private void injectField(String fieldName, Object value) throws Exception {
        Field field = AuditLogObserver.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(observer, value);
    }
}
