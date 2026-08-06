package com.loja.productreviews.adapter.out.persistence;

import com.loja.productreviews.domain.model.Rating;
import com.loja.productreviews.domain.model.Review;

/**
 * Sole place where a {@link Review} domain object is converted to/from
 * {@link ReviewJpaEntity}. Nothing outside the persistence adapter may reach
 * into a JPA entity directly.
 *
 * <p>The {@code @Version} field round-trips through this mapper (lesson #1).
 */
public final class ReviewJpaMapper {

    private ReviewJpaMapper() {}

    public static ReviewJpaEntity toJpa(Review review) {
        ReviewJpaEntity e = new ReviewJpaEntity();
        e.setId(review.getId());
        e.setProductId(review.getProductId());
        e.setAuthorId(review.getAuthorId());
        e.setRating(review.getRating().getValue());
        e.setTitle(review.getTitle());
        e.setBody(review.getBody());
        e.setStatus(review.getStatus());
        e.setVerifiedPurchase(review.isVerifiedPurchase());
        e.setCreatedAt(review.getCreatedAt());
        e.setModeratedAt(review.getModeratedAt());
        e.setRejectionReason(review.getRejectionReason());
        e.setVersion(review.getVersion());
        return e;
    }

    public static Review toDomain(ReviewJpaEntity e) {
        Review review = Review.reconstitute(
                e.getId(),
                e.getProductId(),
                e.getAuthorId(),
                Rating.of(e.getRating()),
                e.getTitle(),
                e.getBody(),
                e.isVerifiedPurchase(),
                e.getCreatedAt(),
                e.getStatus(),
                e.getModeratedAt(),
                e.getRejectionReason());
        review.setVersion(e.getVersion());
        return review;
    }
}
