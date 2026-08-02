package com.loja.productcatalog.adapter.out.persistence;

import com.loja.productcatalog.application.dto.ReservationRequest;
import com.loja.productcatalog.domain.exception.InsufficientStockException;
import com.loja.productcatalog.domain.port.out.InventoryReservationPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * JPA implementation of {@link InventoryReservationPort}. Reserving decrements
 * the product stock through a guarded bulk UPDATE (so two concurrent reserves
 * of the last units cannot oversell) and records a hold row with an expiry. The
 * conditional UPDATE is the single source of truth for "is there stock"; the
 * hold rows only remember how much to give back on release/expiry.
 *
 * <p>Expired holds are released lazily for exactly the products touched by the
 * current operation and globally by the scheduled {@link #expireExpired()}
 * sweep (see {@code ReservationExpiryScheduler}). Callers must run these
 * methods inside a transaction (production: a {@code @Transactional} service;
 * tests: {@code inTx(...)}).
 */
@ApplicationScoped
public class InventoryReservationJpaAdapter implements InventoryReservationPort {

    @PersistenceContext(unitName = "ecommercePU")
    EntityManager em;

    @Override
    public void reserve(String reservationId, List<ReservationRequest> items) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("Reservation id is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }
        if (exists(reservationId)) {
            return;
        }
        Instant expiresAt = Instant.now().plus(DEFAULT_TTL_MINUTES, ChronoUnit.MINUTES);
        for (ReservationRequest item : items) {
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            releaseExpiredFor(item.productId());
            int affected = em.createQuery(
                            "UPDATE ProductJpaEntity p SET p.stock = p.stock - :qty " +
                                    "WHERE p.id = :id AND p.stock >= :qty")
                    .setParameter("qty", item.quantity())
                    .setParameter("id", item.productId())
                    .executeUpdate();
            if (affected == 0) {
                throw new InsufficientStockException(
                        "Insufficient stock for product: " + item.productId());
            }
            em.persist(new InventoryReservationJpaEntity(
                    reservationId, item.productId(), item.quantity(), expiresAt));
        }
    }

    @Override
    public void confirm(String reservationId) {
        em.createQuery("DELETE FROM InventoryReservationJpaEntity r WHERE r.reservationId = :id")
                .setParameter("id", reservationId)
                .executeUpdate();
    }

    @Override
    public void release(String reservationId) {
        List<InventoryReservationJpaEntity> holds = em.createQuery(
                        "SELECT r FROM InventoryReservationJpaEntity r WHERE r.reservationId = :id",
                        InventoryReservationJpaEntity.class)
                .setParameter("id", reservationId)
                .getResultList();
        for (InventoryReservationJpaEntity hold : holds) {
            releaseHold(hold);
        }
    }

    @Override
    public int expireExpired() {
        List<InventoryReservationJpaEntity> expired = em.createQuery(
                        "SELECT r FROM InventoryReservationJpaEntity r WHERE r.expiresAt < :now",
                        InventoryReservationJpaEntity.class)
                .setParameter("now", Instant.now())
                .getResultList();
        for (InventoryReservationJpaEntity hold : expired) {
            releaseHold(hold);
        }
        return expired.size();
    }

    private void releaseExpiredFor(String productId) {
        List<InventoryReservationJpaEntity> expired = em.createQuery(
                        "SELECT r FROM InventoryReservationJpaEntity r " +
                                "WHERE r.productId = :productId AND r.expiresAt < :now",
                        InventoryReservationJpaEntity.class)
                .setParameter("productId", productId)
                .setParameter("now", Instant.now())
                .getResultList();
        for (InventoryReservationJpaEntity hold : expired) {
            releaseHold(hold);
        }
    }

    private void releaseHold(InventoryReservationJpaEntity hold) {
        em.createQuery("UPDATE ProductJpaEntity p SET p.stock = p.stock + :qty WHERE p.id = :id")
                .setParameter("qty", hold.getQuantity())
                .setParameter("id", hold.getProductId())
                .executeUpdate();
        em.remove(hold);
    }

    private boolean exists(String reservationId) {
        return em.createQuery("SELECT COUNT(r) FROM InventoryReservationJpaEntity r " +
                        "WHERE r.reservationId = :id", Long.class)
                .setParameter("id", reservationId)
                .getSingleResult() > 0;
    }
}
