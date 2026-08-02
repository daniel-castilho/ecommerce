package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public Order save(Order order) {
        OrderJpaEntity merged = em.merge(OrderJpaEntity.fromDomain(order));
        em.flush();
        return merged.toDomain();
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(em.find(OrderJpaEntity.class, id))
                .map(OrderJpaEntity::toDomain);
    }
}
