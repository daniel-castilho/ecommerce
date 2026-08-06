package com.loja.productreviews.domain.port.in;

/**
 * Admin-only moderation: reject a {@code PENDING} review with a mandatory reason.
 */
public interface RejectReviewUseCase {

    void reject(String reviewId, String reason);
}
