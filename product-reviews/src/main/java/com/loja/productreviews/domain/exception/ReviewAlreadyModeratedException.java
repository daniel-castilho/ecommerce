package com.loja.productreviews.domain.exception;

/**
 * Thrown when an admin attempts to approve or reject a review that is no
 * longer in the {@link com.loja.productreviews.domain.model.ReviewStatus#PENDING}
 * state (e.g. another moderator already decided).
 */
public class ReviewAlreadyModeratedException extends RuntimeException {

    public ReviewAlreadyModeratedException(String reviewId, String currentStatus) {
        super("Review " + reviewId + " cannot be moderated from status " + currentStatus);
    }
}
