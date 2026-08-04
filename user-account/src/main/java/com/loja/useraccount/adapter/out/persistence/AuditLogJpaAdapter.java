package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.domain.model.AuditLogEvent;
import com.loja.useraccount.domain.port.out.AuditLogPort;
import com.loja.useraccount.domain.port.out.AuditLogQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class AuditLogJpaAdapter implements AuditLogPort, AuditLogQueryPort {

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

    @Override
    public PageResult<AuditLogEvent> findAuditLogs(int page, int pageSize) {
        String countJpql = "SELECT COUNT(a) FROM UserAuditLogJpaEntity a";
        long totalElements = em.createQuery(countJpql, Long.class).getSingleResult();

        String jpql = "SELECT a FROM UserAuditLogJpaEntity a ORDER BY a.createdAt DESC";
        var entities = em.createQuery(jpql, UserAuditLogJpaEntity.class)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        var items = entities.stream().map(e -> new AuditLogEvent(
                e.getId(), e.getUserId(), e.getActorId(), e.getEventType(),
                e.getIpAddress(), e.getUserAgent(), e.getDetails(), e.getCreatedAt()
        )).toList();

        return new PageResult<>(items, totalElements, page, pageSize);
    }
}
