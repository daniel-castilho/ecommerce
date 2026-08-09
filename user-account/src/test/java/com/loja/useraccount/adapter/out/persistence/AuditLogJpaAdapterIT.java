package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.application.dto.AuditLogSearchCriteria;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogJpaAdapterIT extends AbstractIntegrationTest {

    private AuditLogJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new AuditLogJpaAdapter(em);
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.createQuery("DELETE FROM UserAuditLogJpaEntity").executeUpdate();
        tx.commit();
        em.clear();
    }

    @Test
    void shouldPersistAuditLogEntry() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.logEvent("user-1", "admin-9", "LOGIN_SUCCESS", "USER", "user-1",
                "192.168.1.1", "Mozilla/5.0", "Logged in");
        tx.commit();
        em.clear();

        UserAuditLogJpaEntity found = em.createQuery(
                        "SELECT a FROM UserAuditLogJpaEntity a WHERE a.userId = :uid", UserAuditLogJpaEntity.class)
                .setParameter("uid", "user-1")
                .getSingleResult();
        assertThat(found.getEventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(found.getActorId()).isEqualTo("admin-9");
        assertThat(found.getEntityType()).isEqualTo("USER");
        assertThat(found.getEntityId()).isEqualTo("user-1");
        assertThat(found.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(found.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(found.getDetails()).isEqualTo("Logged in");
    }

    @Test
    void shouldPersistMultipleAuditEntries() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.logEvent("user-2", null, "REGISTRATION", "USER", "user-2", null, null, "New user");
        adapter.logEvent("user-2", null, "LOGIN_SUCCESS", "USER", "user-2", "10.0.0.1", "Chrome", "Logged in");
        tx.commit();

        var results = em.createQuery("SELECT a FROM UserAuditLogJpaEntity a WHERE a.userId = :uid", UserAuditLogJpaEntity.class)
                .setParameter("uid", "user-2")
                .getResultList();
        assertThat(results).hasSize(2);
    }

    @Test
    void shouldFilterAuditLogsByActorSubstring() {
        seed("user-a", "admin-1", "LOGIN_SUCCESS", "Actor A login");
        seed("user-b", "admin-2", "LOGIN_SUCCESS", "Actor B login");
        seed("user-a", "admin-2", "REGISTRATION", "Registered");

        var result = adapter.findAuditLogs(
                new AuditLogSearchCriteria("admin-1", null, null, null, null), 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).actorId()).isEqualTo("admin-1");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void shouldFilterAuditLogsByEventType() {
        seed("user-a", "admin-1", "LOGIN_SUCCESS", "Login");
        seed("user-a", "admin-1", "REGISTRATION", "Registered");

        var result = adapter.findAuditLogs(
                new AuditLogSearchCriteria(null, "REGISTRATION", null, null, null), 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).eventType()).isEqualTo("REGISTRATION");
    }

    @Test
    void shouldFilterAuditLogsByDetailsKeyword() {
        seed("user-a", "admin-1", "LOGIN_SUCCESS", "Successful login from browser");
        seed("user-b", "admin-2", "LOGIN_SUCCESS", "Failed login attempt");

        var result = adapter.findAuditLogs(
                new AuditLogSearchCriteria(null, null, "browser", null, null), 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).userId()).isEqualTo("user-a");
    }

    @Test
    void shouldFilterAuditLogsByDateRange() {
        seed("user-a", "admin-1", "LOGIN_SUCCESS", "Older event");
        seed("user-b", "admin-2", "LOGIN_SUCCESS", "Newer event");

        Instant now = Instant.now();
        var result = adapter.findAuditLogs(
                new AuditLogSearchCriteria(null, null, null, now.minusSeconds(5), now.plusSeconds(5)), 0, 20);

        assertThat(result.items()).hasSize(2);
    }

    @Test
    void shouldCombineAllFilters() {
        seed("user-a", "admin-1", "LOGIN_SUCCESS", "Login on chrome");
        seed("user-b", "admin-2", "LOGIN_SUCCESS", "Login on chrome");

        var result = adapter.findAuditLogs(
                new AuditLogSearchCriteria("admin-1", "LOGIN_SUCCESS", "chrome", null, null), 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).actorId()).isEqualTo("admin-1");
    }

    @Test
    void shouldReturnDistinctEventTypes() {
        seed("user-a", "admin-1", "REGISTRATION", "Registered");
        seed("user-a", "admin-1", "LOGIN_SUCCESS", "Login");
        seed("user-b", "admin-2", "LOGIN_SUCCESS", "Login");

        var eventTypes = adapter.distinctEventTypes();

        assertThat(eventTypes).containsExactly("LOGIN_SUCCESS", "REGISTRATION");
    }

    private void seed(String userId, String actorId, String eventType, String details) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.logEvent(userId, actorId, eventType, "USER", userId, "127.0.0.1", "TestAgent", details);
        tx.commit();
        em.clear();
    }
}
