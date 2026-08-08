package com.loja.wishlist.domain.port.in;

/**
 * Authenticated customer removes a product from their personal wishlist.
 *
 * <p>Removal is <em>idempotent</em>: if the product is not on the wishlist,
 * the call is a no-op. Ownership is enforced by matching {@code userId};
 * a user can never remove another user's item.
 */
public interface RemoveFromWishlistUseCase {

    /**
     * Remove a product from the user's wishlist (idempotent).
     *
     * @param userId    authenticated owner (from security context)
     * @param productId product to remove
     */
    void remove(String userId, String productId);
}
