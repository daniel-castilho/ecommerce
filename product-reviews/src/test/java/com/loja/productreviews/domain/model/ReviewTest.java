package com.loja.productreviews.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.loja.productreviews.domain.exception.DuplicateReviewException;
import com.loja.productreviews.domain.exception.ReviewAlreadyModeratedException;

class ReviewTest {

    private static final String PRODUCT_ID = "p-1";
    private static final String AUTHOR_ID = "u-1";

    @Test
    void submit_shouldCreatePendingReview() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(5),
                "Great", "Loved it", true, false);

        assertThat(review.getId()).isNotBlank();
        assertThat(UUID.fromString(review.getId())).isNotNull();
        assertThat(review.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(review.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(review.getRating().getValue()).isEqualTo(5);
        assertThat(review.getTitle()).isEqualTo("Great");
        assertThat(review.getBody()).isEqualTo("Loved it");
        assertThat(review.isVerifiedPurchase()).isTrue();
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(review.getCreatedAt()).isNotNull();
        assertThat(review.getModeratedAt()).isNull();
        assertThat(review.getRejectionReason()).isNull();
    }

    @Test
    void submit_shouldAllowNullTitleAndBody() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(3), null, null, false, false);

        assertThat(review.getTitle()).isNull();
        assertThat(review.getBody()).isNull();
    }

    @Test
    void submit_shouldTrimSurroundingWhitespace() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(4),
                "  ok  ", "  body  ", true, false);

        assertThat(review.getTitle()).isEqualTo("ok");
        assertThat(review.getBody()).isEqualTo("body");
    }

    @Test
    void submit_shouldRejectBlankProductId() {
        assertThatThrownBy(() -> Review.submit("  ", AUTHOR_ID, Rating.of(5), null, null, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void submit_shouldRejectBlankAuthorId() {
        assertThatThrownBy(() -> Review.submit(PRODUCT_ID, null, Rating.of(5), null, null, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorId");
    }

    @Test
    void submit_shouldRejectNullRating() {
        assertThatThrownBy(() -> Review.submit(PRODUCT_ID, AUTHOR_ID, null, null, null, false, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void submit_shouldRejectTitleAbove120Chars() {
        String tooLong = "x".repeat(121);
        assertThatThrownBy(() -> Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(5), tooLong, null, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void submit_shouldRejectBodyAbove2000Chars() {
        String tooLong = "x".repeat(2001);
        assertThatThrownBy(() -> Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(5), null, tooLong, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");
    }

    @Test
    void submit_shouldThrowDuplicateWhenCallerReportsPriorReview() {
        assertThatThrownBy(() -> Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(5), null, null, false, true))
                .isInstanceOf(DuplicateReviewException.class);
    }

    @Test
    void approve_shouldTransitionFromPendingToApprovedAndStampModeratedAt() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(4), null, null, true, false);

        Instant before = Instant.now();
        review.approve();

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(review.getModeratedAt()).isNotNull();
        assertThat(review.getModeratedAt()).isAfterOrEqualTo(before);
        assertThat(review.getRejectionReason()).isNull();
    }

    @Test
    void reject_shouldSetStatusReasonAndModeratedAt() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(1), null, null, false, false);

        review.reject("  spam  ");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(review.getRejectionReason()).isEqualTo("spam");
        assertThat(review.getModeratedAt()).isNotNull();
    }

    @Test
    void reject_shouldRejectBlankReason() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(1), null, null, false, false);

        assertThatThrownBy(() -> review.reject("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void approve_shouldFailWhenNotPending() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(3), null, null, false, false);
        review.approve();

        assertThatThrownBy(review::approve)
                .isInstanceOf(ReviewAlreadyModeratedException.class);
    }

    @Test
    void reject_shouldFailWhenNotPending() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(3), null, null, false, false);
        review.reject("first");

        assertThatThrownBy(() -> review.reject("second"))
                .isInstanceOf(ReviewAlreadyModeratedException.class);
    }

    @Test
    void hide_shouldOnlyBeLegalFromApproved() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(3), null, null, false, false);

        assertThatThrownBy(review::hide)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");

        review.approve();
        review.hide();

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    void hide_shouldFailFromRejected() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(3), null, null, false, false);
        review.reject("spam");

        assertThatThrownBy(review::hide)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reconstitute_shouldRoundTripAllFields() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant moderated = Instant.parse("2026-01-02T00:00:00Z");

        Review review = Review.reconstitute("r-1", PRODUCT_ID, AUTHOR_ID, Rating.of(5),
                "title", "body", true, created, ReviewStatus.APPROVED, moderated, null);

        assertThat(review.getId()).isEqualTo("r-1");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(review.getCreatedAt()).isEqualTo(created);
        assertThat(review.getModeratedAt()).isEqualTo(moderated);
        assertThat(review.getRejectionReason()).isNull();
        assertThat(review.isVerifiedPurchase()).isTrue();
    }

    @Test
    void version_shouldBeAssignableForJpaRoundTrip() {
        Review review = Review.submit(PRODUCT_ID, AUTHOR_ID, Rating.of(5), null, null, false, false);
        assertThat(review.getVersion()).isNull();

        review.setVersion(7L);
        assertThat(review.getVersion()).isEqualTo(7L);
    }
}
