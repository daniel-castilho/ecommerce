package com.loja.productreviews.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.loja.productreviews.domain.exception.DuplicateReviewException;
import com.loja.productreviews.domain.exception.ReviewAlreadyModeratedException;

/**
 * Aggregate root for a customer-authored product review.
 *
 * <p>A {@code Review} is created in {@link ReviewStatus#PENDING} and either
 * approved or rejected by an administrator. The aggregate enforces:
 *
 * <ul>
 *   <li>exactly one review per (authorId, productId) — checked by
 *       {@link #submit} via the caller-supplied uniqueness flag, and
 *       backstopped by a database unique constraint;</li>
 *   <li>moderation transitions are only legal from {@code PENDING}
 *       (see {@link #approve} and {@link #reject});</li>
 *   <li>only an approved review can be hidden by its author
 *       (see {@link #hide}).</li>
 * </ul>
 *
 * <p>The class is otherwise immutable; mutators only flip the
 * status and moderation fields. The {@code version} field is
 * round-tripped through the JPA mapper (lesson #1) for optimistic
 * locking on detached-entity merges.
 */
public class Review {

    private static final int TITLE_MAX_LENGTH = 120;
    private static final int BODY_MAX_LENGTH = 2000;

    private final String id;
    private final String productId;
    private final String authorId;
    private final Rating rating;
    private final String title;
    private final String body;
    private final boolean verifiedPurchase;
    private final Instant createdAt;
    private ReviewStatus status;
    private Instant moderatedAt;
    private String rejectionReason;
    private Long version;

    private Review(String id, String productId, String authorId, Rating rating,
                   String title, String body, boolean verifiedPurchase,
                   Instant createdAt, ReviewStatus status,
                   Instant moderatedAt, String rejectionReason) {
        this.id = id;
        this.productId = productId;
        this.authorId = authorId;
        this.rating = rating;
        this.title = title;
        this.body = body;
        this.verifiedPurchase = verifiedPurchase;
        this.createdAt = createdAt;
        this.status = status;
        this.moderatedAt = moderatedAt;
        this.rejectionReason = rejectionReason;
    }

    /**
     * Build a brand-new {@code Review} from the customer's submission.
     *
     * @param productId       target product
     * @param authorId        authenticated user
     * @param rating          star value (1..5)
     * @param title           optional headline, max 120 chars
     * @param body            optional body, max 2000 chars (already sanitized by the application layer)
     * @param verifiedPurchase true iff the author has purchased the product
     * @param alreadyReviewed true iff the caller already determined that
     *                        (authorId, productId) has no prior review — pass
     *                        {@code false} to force a {@link DuplicateReviewException}
     * @return a new pending review
     */
    public static Review submit(String productId, String authorId, Rating rating,
                                 String title, String body, boolean verifiedPurchase,
                                 boolean alreadyReviewed) {
        requireNonBlank(productId, "productId");
        requireNonBlank(authorId, "authorId");
        Objects.requireNonNull(rating, "rating");
        validateText(title, TITLE_MAX_LENGTH, "title");
        validateText(body, BODY_MAX_LENGTH, "body");
        if (alreadyReviewed) {
            throw new DuplicateReviewException(authorId, productId);
        }
        return new Review(UUID.randomUUID().toString(), productId, authorId, rating,
                normalize(title), normalize(body), verifiedPurchase,
                Instant.now(), ReviewStatus.PENDING, null, null);
    }

    /**
     * Restore a review from persistence. Used by the JPA mapper.
     */
    public static Review reconstitute(String id, String productId, String authorId, Rating rating,
                                      String title, String body, boolean verifiedPurchase,
                                      Instant createdAt, ReviewStatus status,
                                      Instant moderatedAt, String rejectionReason) {
        return new Review(id, productId, authorId, rating,
                title, body, verifiedPurchase,
                createdAt, status, moderatedAt, rejectionReason);
    }

    /**
     * Approve a pending review. Sets status to APPROVED and stamps the
     * moderation timestamp.
     */
    public void approve() {
        ensurePending();
        this.status = ReviewStatus.APPROVED;
        this.moderatedAt = Instant.now();
        this.rejectionReason = null;
    }

    /**
     * Reject a pending review with the given reason (required, non-blank).
     */
    public void reject(String reason) {
        ensurePending();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        this.status = ReviewStatus.REJECTED;
        this.rejectionReason = reason.trim();
        this.moderatedAt = Instant.now();
    }

    /**
     * Soft-delete an approved review by its author. Only legal from APPROVED.
     */
    public void hide() {
        if (status != ReviewStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED reviews can be hidden (was " + status + ")");
        }
        this.status = ReviewStatus.HIDDEN;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getAuthorId() { return authorId; }
    public Rating getRating() { return rating; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isVerifiedPurchase() { return verifiedPurchase; }
    public Instant getCreatedAt() { return createdAt; }
    public ReviewStatus getStatus() { return status; }
    public Instant getModeratedAt() { return moderatedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    private void ensurePending() {
        if (status != ReviewStatus.PENDING) {
            throw new ReviewAlreadyModeratedException(id, status.name());
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void validateText(String value, int maxLength, String name) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
