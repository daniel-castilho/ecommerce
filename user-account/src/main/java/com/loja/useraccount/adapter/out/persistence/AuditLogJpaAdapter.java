package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.application.dto.AuditLogSearchCriteria;
import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.domain.model.AuditLogEvent;
import com.loja.useraccount.domain.port.out.AuditLogPort;
import com.loja.useraccount.domain.port.out.AuditLogQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public void logEvent(String userId, String actorId, String eventType, String entityType, String entityId,
                         String ipAddress, String userAgent, String details) {
        UserAuditLogJpaEntity entity = new UserAuditLogJpaEntity(
                userId, actorId, eventType, entityType, entityId, ipAddress, userAgent, details);
        em.persist(entity);
    }

    @Override
    public PageResult<AuditLogEvent> findAuditLogs(AuditLogSearchCriteria criteria, int page, int pageSize) {
        AuditLogSearchCriteria effective = criteria != null ? criteria : AuditLogSearchCriteria.empty();
        String whereClause = whereClause(effective);
        Map<String, Object> params = params(effective);

        TypedQuery<Long> countQuery = em.createQuery(
                "SELECT COUNT(a) FROM UserAuditLogJpaEntity a" + whereClause, Long.class);
        params.forEach(countQuery::setParameter);
        long totalElements = countQuery.getSingleResult();
        TypedQuery<UserAuditLogJpaEntity> query = em.createQuery(
                "SELECT a FROM UserAuditLogJpaEntity a" + whereClause + " ORDER BY a.createdAt DESC",
                UserAuditLogJpaEntity.class);
        params.forEach(query::setParameter);
        var entities = query
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        var items = entities.stream().map(e -> new AuditLogEvent(
                e.getId(), e.getUserId(), e.getActorId(), e.getEventType(),
                e.getEntityType(), e.getEntityId(),
                e.getIpAddress(), e.getUserAgent(), e.getDetails(), e.getCreatedAt()
        )).toList();

        return new PageResult<>(items, totalElements, page, pageSize);
    }

    @Override
    public List<String> distinctEventTypes() {
        return em.createQuery("SELECT DISTINCT a.eventType FROM UserAuditLogJpaEntity a ORDER BY a.eventType", String.class)
                .getResultList();
    }

    private String whereClause(AuditLogSearchCriteria criteria) {
        List<String> predicates = new ArrayList<>();
        if (criteria.actorId() != null && !criteria.actorId().isBlank()) {
            predicates.add("a.actorId LIKE :actorId");
        }
        if (criteria.eventType() != null && !criteria.eventType().isBlank()) {
            predicates.add("a.eventType = :eventType");
        }
        if (criteria.detailsKeyword() != null && !criteria.detailsKeyword().isBlank()) {
            predicates.add("a.details LIKE :detailsKeyword");
        }
        if (criteria.from() != null) {
            predicates.add("a.createdAt >= :startInstant");
        }
        if (criteria.to() != null) {
            predicates.add("a.createdAt <= :endInstant");
        }
        return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
    }

    private Map<String, Object> params(AuditLogSearchCriteria criteria) {
        Map<String, Object> params = new HashMap<>();
        if (criteria.actorId() != null && !criteria.actorId().isBlank()) {
            params.put("actorId", "%" + criteria.actorId().trim() + "%");
        }
        if (criteria.eventType() != null && !criteria.eventType().isBlank()) {
            params.put("eventType", criteria.eventType());
        }
        if (criteria.detailsKeyword() != null && !criteria.detailsKeyword().isBlank()) {
            params.put("detailsKeyword", "%" + criteria.detailsKeyword().trim() + "%");
        }
        if (criteria.from() != null) {
            params.put("startInstant", criteria.from());
        }
        if (criteria.to() != null) {
            params.put("endInstant", criteria.to());
        }
        return params;
    }
}
