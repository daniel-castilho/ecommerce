package com.loja.useraccount.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class AuditLogJpaAdapterTest {

    @Mock
    private EntityManager em;

    @Captor
    private ArgumentCaptor<UserAuditLogJpaEntity> entityCaptor;

    private AuditLogJpaAdapter adapter;
    private AutoCloseable mockCloseable;

    @BeforeEach
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);
        adapter = new AuditLogJpaAdapter(em);
    }

    @Test
    void shouldPersistAuditLogEntry() {
        adapter.logEvent("user-1", null, "LOGIN_SUCCESS", "192.168.1.1", "Mozilla/5.0", "Logged in");

        verify(em).persist(entityCaptor.capture());
        UserAuditLogJpaEntity entity = entityCaptor.getValue();

        assertThat(entity.getUserId()).isEqualTo("user-1");
        assertThat(entity.getEventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(entity.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(entity.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(entity.getDetails()).isEqualTo("Logged in");
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldPersistAuditLogWithNullIpAndUserAgent() {
        adapter.logEvent("user-2", null, "REGISTRATION", null, null, "New user registered");

        verify(em).persist(entityCaptor.capture());
        UserAuditLogJpaEntity entity = entityCaptor.getValue();

        assertThat(entity.getUserId()).isEqualTo("user-2");
        assertThat(entity.getIpAddress()).isNull();
        assertThat(entity.getUserAgent()).isNull();
    }

    @Test
    void shouldPersistAuditLogWithActorId() {
        adapter.logEvent("user-3", "admin-9", "ROLE_ASSIGNED", null, null, "Role assigned: ADMIN");

        verify(em).persist(entityCaptor.capture());
        UserAuditLogJpaEntity entity = entityCaptor.getValue();

        assertThat(entity.getUserId()).isEqualTo("user-3");
        assertThat(entity.getActorId()).isEqualTo("admin-9");
        assertThat(entity.getEventType()).isEqualTo("ROLE_ASSIGNED");
    }
}
