package com.loja.productreviews.domain.exception;

/**
 * Thrown when a review cannot be found by the given id.
 */
public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(String reviewId) {
        super("Review not found: " + reviewId);
    }
}
