package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.out.RefundRequestRepositoryPort;
import com.loja.shared.domain.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class RefundRequestJpaAdapter implements RefundRequestRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    private EntityManager em;

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
    public PageResult<RefundRequest> findAll(int page, int pageSize) {
        String countJpql = "SELECT COUNT(r) FROM RefundRequestJpaEntity r";
        long totalElements = em.createQuery(countJpql, Long.class).getSingleResult();

        String jpql = "SELECT r FROM RefundRequestJpaEntity r ORDER BY r.createdAt DESC";
        List<RefundRequestJpaEntity> entities = em.createQuery(jpql, RefundRequestJpaEntity.class)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        List<RefundRequest> items = entities.stream().map(this::mapToDomain).toList();
        return new PageResult<>(items, totalElements, page, pageSize);
    }

    @Override
    public PageResult<RefundRequest> findByStatus(RefundStatus status, int page, int pageSize) {
        String countJpql = "SELECT COUNT(r) FROM RefundRequestJpaEntity r WHERE r.status = :status";
        long totalElements = em.createQuery(countJpql, Long.class)
                .setParameter("status", status)
                .getSingleResult();

        String jpql = "SELECT r FROM RefundRequestJpaEntity r WHERE r.status = :status ORDER BY r.createdAt DESC";
        List<RefundRequestJpaEntity> entities = em.createQuery(jpql, RefundRequestJpaEntity.class)
                .setParameter("status", status)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        List<RefundRequest> items = entities.stream().map(this::mapToDomain).toList();
        return new PageResult<>(items, totalElements, page, pageSize);
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
