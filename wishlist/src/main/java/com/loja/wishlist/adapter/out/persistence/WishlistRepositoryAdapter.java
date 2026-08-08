package com.loja.wishlist.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import com.loja.wishlist.domain.exception.DuplicateWishlistItemException;
import com.loja.wishlist.domain.model.WishlistItem;
import com.loja.wishlist.domain.port.out.WishlistRepositoryPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;

/**
 * JPA-backed implementation of {@link WishlistRepositoryPort}.
 *
 * <p>The unique {@code (user_id, product_id)} constraint is enforced by the
 * application service (idempotent add); the database constraint is the safety
 * net that surfaces as {@link DuplicateWishlistItemException}.
 *
 * <p>Pagination / list queries use {@code getResultList()} (never
 * {@code getResultStream()}) — see {@code docs/lessons.md}.
 */
@ApplicationScoped
public class WishlistRepositoryAdapter implements WishlistRepositoryPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public WishlistItem save(WishlistItem item) {
        try {
            WishlistItemJpaEntity merged = em.merge(WishlistItemJpaMapper.toJpa(item));
            em.flush();
            return WishlistItemJpaMapper.toDomain(merged);
        } catch (PersistenceException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new DuplicateWishlistItemException(item.getUserId(), item.getProductId());
            }
            throw e;
        }
    }

    @Override
    public void deleteByUserAndProduct(String userId, String productId) {
        em.createQuery(
                        "DELETE FROM WishlistItemJpaEntity w "
                                + "WHERE w.userId = :userId AND w.productId = :productId")
                .setParameter("userId", userId)
                .setParameter("productId", productId)
                .executeUpdate();
    }

    @Override
    public Optional<WishlistItem> findByUserAndProduct(String userId, String productId) {
        TypedQuery<WishlistItemJpaEntity> query = em.createQuery(
                "SELECT w FROM WishlistItemJpaEntity w "
                        + "WHERE w.userId = :userId AND w.productId = :productId",
                WishlistItemJpaEntity.class);
        query.setParameter("userId", userId);
        query.setParameter("productId", productId);
        List<WishlistItemJpaEntity> results = query.getResultList();
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(WishlistItemJpaMapper.toDomain(results.get(0)));
    }

    @Override
    public List<WishlistItem> findByUserIdOrderByCreatedAtDesc(String userId) {
        TypedQuery<WishlistItemJpaEntity> query = em.createQuery(
                "SELECT w FROM WishlistItemJpaEntity w "
                        + "WHERE w.userId = :userId "
                        + "ORDER BY w.createdAt DESC",
                WishlistItemJpaEntity.class);
        query.setParameter("userId", userId);
        return query.getResultList().stream()
                .map(WishlistItemJpaMapper::toDomain)
                .toList();
    }

    @Override
    public boolean exists(String userId, String productId) {
        Long count = em.createQuery(
                        "SELECT COUNT(w) FROM WishlistItemJpaEntity w "
                                + "WHERE w.userId = :userId AND w.productId = :productId",
                        Long.class)
                .setParameter("userId", userId)
                .setParameter("productId", productId)
                .getSingleResult();
        return count != null && count > 0;
    }

    private static boolean isUniqueConstraintViolation(PersistenceException e) {
        Throwable cause = e;
        while (cause != null) {
            String name = cause.getClass().getSimpleName();
            if ((name.endsWith("ConstraintViolationException") || name.endsWith("IntegrityConstraintViolation"))
                    && cause.getMessage() != null) {
                String msg = cause.getMessage().toLowerCase();
                if (msg.contains("uk_wishlist_item_user_product")
                        || msg.contains("(user_id, product_id)")
                        || msg.contains("user_id, product_id")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
