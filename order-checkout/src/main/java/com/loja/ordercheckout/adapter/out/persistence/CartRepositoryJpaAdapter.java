package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.exception.CartConcurrentModificationException;
import com.loja.ordercheckout.domain.model.Cart;
import com.loja.ordercheckout.domain.port.out.CartRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link CartRepositoryPort}.
 *
 * <p>MVP keeps a single active cart per user: {@code user_id} is unique both at
 * the database level (migration V24) and through the {@code @Column(unique =
 * true)} mapping used by schema-generation in tests.
 *
 * <p>Queries use {@code getResultList()} (never {@code getResultStream()}) —
 * see {@code docs/lessons.md}. Optimistic-lock conflicts surface as
 * {@link CartConcurrentModificationException}.
 */
@ApplicationScoped
public class CartRepositoryJpaAdapter implements CartRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public Optional<Cart> findByUserId(String userId) {
        List<CartJpaEntity> results = em.createQuery(
                        "SELECT c FROM CartJpaEntity c WHERE c.userId = :userId",
                        CartJpaEntity.class)
                .setParameter("userId", userId)
                .getResultList();
        return results.stream().findFirst().map(CartJpaEntity::toDomain);
    }

    @Override
    public Cart save(Cart cart) {
        try {
            CartJpaEntity merged = em.merge(CartJpaEntity.fromDomain(cart));
            em.flush();
            return merged.toDomain();
        } catch (OptimisticLockException e) {
            throw new CartConcurrentModificationException(cart.getId());
        }
    }

    @Override
    public void deleteByUserId(String userId) {
        em.createQuery("DELETE FROM CartJpaEntity c WHERE c.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
