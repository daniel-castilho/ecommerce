package com.loja.productreviews.domain.port.in;

/**
 * Customer soft-deletes their own approved review.
 *
 * <p>Throws {@link com.loja.productreviews.domain.exception.ReviewNotFoundException}
 * if the id is unknown, or {@link IllegalStateException} if the review is
 * not currently APPROVED or does not belong to the caller.
 */
public interface HideOwnReviewUseCase {

    void hide(String reviewId, String authorId);
}
