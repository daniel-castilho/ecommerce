package com.loja.wishlist.domain.port.in;

/**
 * Authenticated customer adds a product to their personal wishlist.
 *
 * <p><b>Given</b> an authenticated user id and a product id, when the product
 * exists and is ACTIVE, then the product is saved on the user's wishlist.
 * Adding a product that is already present is <em>idempotent</em>: the existing
 * item id is returned and no duplicate row is created.
 *
 * <p>Throws
 * {@link com.loja.wishlist.domain.exception.ProductNotAvailableException}
 * if the product does not exist or is not ACTIVE.
 */
public interface AddToWishlistUseCase {

    /**
     * Add a product to the user's wishlist (idempotent).
     *
     * @param userId    authenticated owner (from security context, never from the form alone)
     * @param productId target product
     * @return the wishlist item id (new or existing)
     */
    String add(String userId, String productId);
}
