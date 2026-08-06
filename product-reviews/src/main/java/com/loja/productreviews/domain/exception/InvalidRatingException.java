package com.loja.productreviews.domain.exception;

/**
 * Thrown when a rating value falls outside the accepted range [1, 5].
 */
public class InvalidRatingException extends RuntimeException {

    public InvalidRatingException(String message) {
        super(message);
    }
}
