package com.loja.productreviews.domain.exception;

/**
 * Thrown when a customer attempts to submit more than one review for the
 * same product. Enforced by the domain (see {@code Review.submit}) and
 * by a unique database constraint as a backstop.
 */
public class DuplicateReviewException extends RuntimeException {

    public DuplicateReviewException(String userId, String productId) {
        super("User " + userId + " has already reviewed product " + productId);
    }
}
