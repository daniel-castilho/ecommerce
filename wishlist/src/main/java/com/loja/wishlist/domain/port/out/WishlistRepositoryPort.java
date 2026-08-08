package com.loja.wishlist.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.loja.wishlist.domain.model.WishlistItem;

/**
 * Persistence port for {@link WishlistItem}.
 *
 * <p>Implementations must enforce the unique {@code (userId, productId)}
 * constraint at the database level as a backstop for concurrent inserts.
 */
public interface WishlistRepositoryPort {

    /** Persist a new wishlist item; returns the same instance for chaining. */
    WishlistItem save(WishlistItem item);

    /**
     * Delete the item for {@code (userId, productId)} if it exists.
     * No-op when missing (idempotent).
     */
    void deleteByUserAndProduct(String userId, String productId);

    Optional<WishlistItem> findByUserAndProduct(String userId, String productId);

    /** All items for the user, newest first. */
    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(String userId);

    /** True iff the user already has this product on their wishlist. */
    boolean exists(String userId, String productId);
}
