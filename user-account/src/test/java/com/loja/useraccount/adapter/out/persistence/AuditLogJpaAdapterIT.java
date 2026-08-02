package com.loja.useraccount.adapter.out.persistence;

import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogJpaAdapterIT extends AbstractIntegrationTest {

    private AuditLogJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new AuditLogJpaAdapter(em);
    }

    @Test
    void shouldPersistAuditLogEntry() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.logEvent("user-1", "admin-9", "LOGIN_SUCCESS", "192.168.1.1", "Mozilla/5.0", "Logged in");
        tx.commit();
        em.clear();

        UserAuditLogJpaEntity found = em.createQuery(
                        "SELECT a FROM UserAuditLogJpaEntity a WHERE a.userId = :uid", UserAuditLogJpaEntity.class)
                .setParameter("uid", "user-1")
                .getSingleResult();
        assertThat(found.getEventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(found.getActorId()).isEqualTo("admin-9");
        assertThat(found.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(found.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(found.getDetails()).isEqualTo("Logged in");
    }

    @Test
    void shouldPersistMultipleAuditEntries() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.logEvent("user-2", null, "REGISTRATION", null, null, "New user");
        adapter.logEvent("user-2", null, "LOGIN_SUCCESS", "10.0.0.1", "Chrome", "Logged in");
        tx.commit();

        var results = em.createQuery("SELECT a FROM UserAuditLogJpaEntity a WHERE a.userId = :uid", UserAuditLogJpaEntity.class)
                .setParameter("uid", "user-2")
                .getResultList();
        assertThat(results).hasSize(2);
    }
}
