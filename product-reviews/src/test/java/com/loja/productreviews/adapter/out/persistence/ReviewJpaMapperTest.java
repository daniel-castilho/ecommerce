package com.loja.productreviews.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.loja.productreviews.domain.model.Rating;
import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.model.ReviewStatus;

class ReviewJpaMapperTest {

    @Test
    void shouldRoundTripPendingReview() {
        Review review = Review.submit("p-1", "u-1", Rating.of(4), "Title", "Body", true, false);

        ReviewJpaEntity jpa = ReviewJpaMapper.toJpa(review);
        assertThat(jpa.getId()).isEqualTo(review.getId());
        assertThat(jpa.getProductId()).isEqualTo("p-1");
        assertThat(jpa.getAuthorId()).isEqualTo("u-1");
        assertThat(jpa.getRating()).isEqualTo(4);
        assertThat(jpa.getTitle()).isEqualTo("Title");
        assertThat(jpa.getBody()).isEqualTo("Body");
        assertThat(jpa.isVerifiedPurchase()).isTrue();
        assertThat(jpa.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(jpa.getCreatedAt()).isEqualTo(review.getCreatedAt());
        assertThat(jpa.getModeratedAt()).isNull();
        assertThat(jpa.getRejectionReason()).isNull();

        Review restored = ReviewJpaMapper.toDomain(jpa);
        assertThat(restored.getId()).isEqualTo(review.getId());
        assertThat(restored.getRating()).isEqualTo(Rating.of(4));
        assertThat(restored.getCreatedAt()).isEqualTo(review.getCreatedAt());
        assertThat(restored.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(restored.getRejectionReason()).isNull();
    }

    @Test
    void shouldRoundTripModeratedReviewWithVersion() {
        Review review = Review.submit("p-1", "u-1", Rating.of(2), null, null, false, false);
        review.reject("spam");

        ReviewJpaEntity jpa = ReviewJpaMapper.toJpa(review);
        jpa.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        jpa.setVersion(3L);

        Review restored = ReviewJpaMapper.toDomain(jpa);
        assertThat(restored.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(restored.getRejectionReason()).isEqualTo("spam");
        assertThat(restored.getModeratedAt()).isNotNull();
        assertThat(restored.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(restored.getVersion()).isEqualTo(3L);
    }
}
