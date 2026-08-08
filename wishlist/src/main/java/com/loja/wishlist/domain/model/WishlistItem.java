package com.loja.wishlist.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate for a single entry on a customer's personal wishlist.
 *
 * <p>Uniqueness of {@code (userId, productId)} is enforced by the application
 * service (idempotent add) and backstopped by a database unique constraint.
 * The domain only validates that required identifiers are present.
 */
public class WishlistItem {

    private final String id;
    private final String userId;
    private final String productId;
    private final Instant createdAt;

    private WishlistItem(String id, String userId, String productId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.createdAt = createdAt;
    }

    /**
     * Create a brand-new wishlist entry for the given owner and product.
     *
     * @param userId    authenticated owner
     * @param productId target product
     * @return a new item with a generated id and {@code createdAt = now}
     */
    public static WishlistItem create(String userId, String productId) {
        requireNonBlank(userId, "userId");
        requireNonBlank(productId, "productId");
        return new WishlistItem(
                UUID.randomUUID().toString(),
                userId.trim(),
                productId.trim(),
                Instant.now());
    }

    /**
     * Restore a wishlist item from persistence. Used by the JPA mapper.
     */
    public static WishlistItem reconstitute(String id, String userId, String productId, Instant createdAt) {
        requireNonBlank(id, "id");
        requireNonBlank(userId, "userId");
        requireNonBlank(productId, "productId");
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        return new WishlistItem(id, userId, productId, createdAt);
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getProductId() {
        return productId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
