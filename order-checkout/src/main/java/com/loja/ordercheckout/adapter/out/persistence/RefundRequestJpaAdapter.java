package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.application.dto.RefundSearchCriteria;
import com.loja.ordercheckout.application.dto.RefundSort;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.port.out.RefundRequestRepositoryPort;
import com.loja.shared.domain.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class RefundRequestJpaAdapter implements RefundRequestRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public void save(RefundRequest request) {
        RefundRequestJpaEntity entity = new RefundRequestJpaEntity(
                request.getId(),
                request.getOrderId(),
                request.getAmount().getAmount(),
                request.getReason(),
                request.getStatus(),
                request.getRejectionReason(),
                request.getCreatedAt(),
                request.getProcessedAt()
        );
        em.merge(entity);
    }

    @Override
    public Optional<RefundRequest> findById(String id) {
        RefundRequestJpaEntity entity = em.find(RefundRequestJpaEntity.class, id);
        return Optional.ofNullable(entity).map(this::mapToDomain);
    }

    @Override
    public PageResult<RefundRequest> find(RefundSearchCriteria criteria, int page, int pageSize) {
        RefundSearchCriteria effective = criteria != null ? criteria : RefundSearchCriteria.empty();
        String whereClause = whereClause(effective);
        Map<String, Object> params = params(effective);

        TypedQuery<Long> countQuery = em.createQuery(
                "SELECT COUNT(r) FROM RefundRequestJpaEntity r" + whereClause, Long.class);
        params.forEach(countQuery::setParameter);
        long totalElements = countQuery.getSingleResult();

        TypedQuery<RefundRequestJpaEntity> query = em.createQuery(
                "SELECT r FROM RefundRequestJpaEntity r" + whereClause + " ORDER BY " + orderBy(effective),
                RefundRequestJpaEntity.class);
        params.forEach(query::setParameter);
        List<RefundRequestJpaEntity> entities = query
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        List<RefundRequest> items = entities.stream().map(this::mapToDomain).toList();
        return new PageResult<>(items, totalElements, page, pageSize);
    }

    private String whereClause(RefundSearchCriteria criteria) {
        List<String> predicates = new ArrayList<>();
        if (criteria.status() != null) {
            predicates.add("r.status = :status");
        }
        if (criteria.customerQuery() != null && !criteria.customerQuery().isBlank()) {
            predicates.add("EXISTS (SELECT o FROM OrderJpaEntity o WHERE o.id = r.orderId "
                    + "AND (o.customerEmail LIKE :customerQuery "
                    + "OR o.shippingAddress.recipientName LIKE :customerQuery))");
        }
        if (criteria.from() != null) {
            predicates.add("r.createdAt >= :fromInstant");
        }
        if (criteria.to() != null) {
            predicates.add("r.createdAt <= :toInstant");
        }
        return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
    }

    private Map<String, Object> params(RefundSearchCriteria criteria) {
        Map<String, Object> params = new HashMap<>();
        if (criteria.status() != null) {
            params.put("status", criteria.status());
        }
        if (criteria.customerQuery() != null && !criteria.customerQuery().isBlank()) {
            params.put("customerQuery", "%" + criteria.customerQuery().trim() + "%");
        }
        if (criteria.from() != null) {
            params.put("fromInstant", criteria.from());
        }
        if (criteria.to() != null) {
            params.put("toInstant", criteria.to());
        }
        return params;
    }

    private String orderBy(RefundSearchCriteria criteria) {
        String column = criteria.sort() == RefundSort.AMOUNT ? "r.amount" : "r.createdAt";
        return column + (criteria.ascending() ? " ASC" : " DESC");
    }

    @Override
    public List<RefundRequest> findByOrderId(String orderId) {
        String jpql = "SELECT r FROM RefundRequestJpaEntity r WHERE r.orderId = :orderId "
                + "ORDER BY r.createdAt DESC";
        return em.createQuery(jpql, RefundRequestJpaEntity.class)
                .setParameter("orderId", orderId)
                .getResultList()
                .stream()
                .map(this::mapToDomain)
                .toList();
    }

    private RefundRequest mapToDomain(RefundRequestJpaEntity entity) {
        return RefundRequest.reconstitute(
                entity.getId(),
                entity.getOrderId(),
                new Money(entity.getAmount()),
                entity.getReason(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getCreatedAt(),
                entity.getProcessedAt()
        );
    }
}
