package com.loja.productreviews.domain.port.in;

/**
 * Admin-only moderation: approve a {@code PENDING} review.
 *
 * <p>Throws {@link com.loja.productreviews.domain.exception.ReviewNotFoundException}
 * if the id is unknown, or
 * {@link com.loja.productreviews.domain.exception.ReviewAlreadyModeratedException}
 * if the review is no longer pending.
 */
public interface ApproveReviewUseCase {

    void approve(String reviewId);
}
