package com.loja.wishlist.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA mapping for {@link com.loja.wishlist.domain.model.WishlistItem}.
 *
 * <p>Does not extend another module's auditable base entity — modules must
 * never reach into each other's adapter packages (AGENTS.md rule #2).
 * {@code createdAt} is owned by the domain (captured in
 * {@code WishlistItem.create}) so the JPA entity mirrors it directly.
 */
@Entity
@Table(name = "tb_wishlist_item", uniqueConstraints = @UniqueConstraint(
        name = "uk_wishlist_item_user_product",
        columnNames = {"user_id", "product_id"}))
public class WishlistItemJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WishlistItemJpaEntity() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
