package com.loja.productcatalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A hold row backing {@link com.loja.productcatalog.domain.port.out.InventoryReservationPort}.
 * One row per (reservation, product); the reserved units are already subtracted
 * from the product stock, so releasing a hold re-adds them.
 */
@Entity
@Table(name = "inventory_reservation",
        uniqueConstraints = @UniqueConstraint(name = "uq_inventory_reservation_product",
                columnNames = {"reservation_id", "product_id"}))
public class InventoryReservationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, length = 64)
    private String reservationId;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected InventoryReservationJpaEntity() { }

    public InventoryReservationJpaEntity(String reservationId, String productId, int quantity,
                                         Instant expiresAt) {
        this.reservationId = reservationId;
        this.productId = productId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getReservationId() { return reservationId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Instant getExpiresAt() { return expiresAt; }
}
