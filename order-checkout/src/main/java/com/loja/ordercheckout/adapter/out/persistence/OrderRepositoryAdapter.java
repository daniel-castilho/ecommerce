package com.loja.ordercheckout.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.exception.OrderConcurrentModificationException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.shared.domain.Money;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private static final List<OrderStatus> EXCLUDED_FROM_REVENUE = List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);

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
    public PageResult<Order> findAll(int page, int pageSize) {
        long totalElements = em.createQuery("SELECT COUNT(o) FROM OrderJpaEntity o", Long.class)
                .getSingleResult();

        int safePage = Math.max(page, 0);
        int safePageSize = pageSize <= 0 ? PageResult.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, PageResult.MAX_PAGE_SIZE);

        List<Order> items = em.createQuery(
                        "SELECT o FROM OrderJpaEntity o ORDER BY o.createdAt DESC",
                        OrderJpaEntity.class)
                .setFirstResult(safePage * safePageSize)
                .setMaxResults(safePageSize)
                .getResultList()
                .stream()
                .map(OrderJpaEntity::toDomain)
                .toList();

        return new PageResult<>(items, totalElements, safePage, safePageSize);
    }

    @Override
    public PageResult<Order> findByStatus(OrderStatus status, int page, int pageSize) {
        long totalElements = em.createQuery(
                        "SELECT COUNT(o) FROM OrderJpaEntity o WHERE o.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();

        int safePage = Math.max(page, 0);
        int safePageSize = pageSize <= 0 ? PageResult.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, PageResult.MAX_PAGE_SIZE);

        List<Order> items = em.createQuery(
                        "SELECT o FROM OrderJpaEntity o WHERE o.status = :status ORDER BY o.createdAt DESC",
                        OrderJpaEntity.class)
                .setParameter("status", status)
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

    @Override
    public Money revenueSince(Instant since) {
        BigDecimal itemsRevenue = em.createQuery(
                        "SELECT COALESCE(SUM(ol.quantity * ol.unitPrice), 0) " +
                                "FROM OrderJpaEntity o JOIN o.items ol " +
                                "WHERE o.createdAt >= :since AND o.status NOT IN :excluded",
                        BigDecimal.class)
                .setParameter("since", since)
                .setParameter("excluded", EXCLUDED_FROM_REVENUE)
                .getSingleResult();
        BigDecimal shippingRevenue = em.createQuery(
                        "SELECT COALESCE(SUM(o.shippingCost), 0) " +
                                "FROM OrderJpaEntity o " +
                                "WHERE o.createdAt >= :since AND o.status NOT IN :excluded",
                        BigDecimal.class)
                .setParameter("since", since)
                .setParameter("excluded", EXCLUDED_FROM_REVENUE)
                .getSingleResult();
        return new Money(itemsRevenue.add(shippingRevenue));
    }

    @Override
    public long countCreatedSince(Instant since) {
        return em.createQuery(
                        "SELECT COUNT(o) FROM OrderJpaEntity o WHERE o.createdAt >= :since", Long.class)
                .setParameter("since", since)
                .getSingleResult();
    }

    @Override
    public Map<OrderStatus, Long> countByStatus() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT o.status, COUNT(o) FROM OrderJpaEntity o GROUP BY o.status", Object[].class)
                .getResultList();
        for (Object[] row : rows) {
            counts.put((OrderStatus) row[0], (Long) row[1]);
        }
        return counts;
    }
}
