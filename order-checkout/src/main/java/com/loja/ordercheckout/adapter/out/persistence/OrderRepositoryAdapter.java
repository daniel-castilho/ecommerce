package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.exception.OrderConcurrentModificationException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public Order save(Order order) {
        try {
            OrderJpaEntity merged = em.merge(OrderJpaEntity.fromDomain(order));
            em.flush();
            return merged.toDomain();
        } catch (OptimisticLockException e) {
            throw new OrderConcurrentModificationException(order.getId());
        }
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(em.find(OrderJpaEntity.class, id))
                .map(OrderJpaEntity::toDomain);
    }

    @Override
    public PageResult<Order> findByCustomerId(String customerId, int page, int pageSize) {
        long totalElements = em.createQuery(
                        "SELECT COUNT(o) FROM OrderJpaEntity o WHERE o.userId = :customerId", Long.class)
                .setParameter("customerId", customerId)
                .getSingleResult();

        int safePage = Math.max(page, 0);
        int safePageSize = pageSize <= 0 ? PageResult.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, PageResult.MAX_PAGE_SIZE);

        List<Order> items = em.createQuery(
                        "SELECT o FROM OrderJpaEntity o WHERE o.userId = :customerId ORDER BY o.createdAt DESC",
                        OrderJpaEntity.class)
                .setParameter("customerId", customerId)
                .setFirstResult(safePage * safePageSize)
                .setMaxResults(safePageSize)
                .getResultList()
                .stream()
                .map(OrderJpaEntity::toDomain)
                .toList();

        return new PageResult<>(items, totalElements, safePage, safePageSize);
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return em.createQuery(
                        "SELECT o FROM OrderJpaEntity o WHERE o.status = :status ORDER BY o.createdAt DESC",
                        OrderJpaEntity.class)
                .setParameter("status", status)
                .getResultList()
                .stream()
                .map(OrderJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countAll() {
        return em.createQuery("SELECT COUNT(o) FROM OrderJpaEntity o", Long.class)
                .getSingleResult();
    }
}
