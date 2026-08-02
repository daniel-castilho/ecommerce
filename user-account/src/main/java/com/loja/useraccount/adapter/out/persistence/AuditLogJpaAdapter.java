package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.domain.port.out.AuditLogPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class AuditLogJpaAdapter implements AuditLogPort {

    @PersistenceContext(unitName = "ecommercePU")
    private EntityManager em;

    protected AuditLogJpaAdapter() {}

    AuditLogJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public void logEvent(String userId, String actorId, String eventType, String ipAddress, String userAgent, String details) {
        UserAuditLogJpaEntity entity = new UserAuditLogJpaEntity(userId, actorId, eventType, ipAddress, userAgent, details);
        em.persist(entity);
    }
}
