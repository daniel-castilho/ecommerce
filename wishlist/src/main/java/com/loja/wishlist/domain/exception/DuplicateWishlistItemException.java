package com.loja.wishlist.domain.exception;

/**
 * Thrown when a customer attempts to add a product that is already on their
 * wishlist. The application service treats add as idempotent in the happy path;
 * this exception is the safety net for concurrent inserts that hit the unique
 * database constraint.
 */
public class DuplicateWishlistItemException extends RuntimeException {

    public DuplicateWishlistItemException(String userId, String productId) {
        super("User " + userId + " already has product " + productId + " on their wishlist");
    }
}
