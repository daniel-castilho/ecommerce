package com.loja.wishlist.domain.exception;

/**
 * Thrown when a wishlist item cannot be found by id. Remove-by-product is
 * idempotent and does not raise this exception.
 */
public class WishlistItemNotFoundException extends RuntimeException {

    public WishlistItemNotFoundException(String itemId) {
        super("Wishlist item not found: " + itemId);
    }
}
