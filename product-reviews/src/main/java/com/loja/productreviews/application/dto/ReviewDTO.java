package com.loja.productreviews.application.dto;

import java.time.Instant;

import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.model.ReviewStatus;

/**
 * Public-facing representation of a {@link Review}.
 *
 * <p>Used by both the customer product page and the admin moderation queue.
 * Internal-only fields (e.g. {@code rejectionReason}, {@code moderatedAt}) are
 * intentionally included so admins can read them from the same DTO.
 */
public record ReviewDTO(
        String id,
        String productId,
        String authorId,
        int rating,
        String title,
        String body,
        boolean verifiedPurchase,
        ReviewStatus status,
        Instant createdAt,
        Instant moderatedAt,
        String rejectionReason) {

    public static ReviewDTO from(Review review) {
        return new ReviewDTO(
                review.getId(),
                review.getProductId(),
                review.getAuthorId(),
                review.getRating().getValue(),
                review.getTitle(),
                review.getBody(),
                review.isVerifiedPurchase(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getModeratedAt(),
                review.getRejectionReason());
    }
}
