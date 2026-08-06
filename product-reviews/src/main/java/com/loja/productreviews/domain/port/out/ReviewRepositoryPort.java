package com.loja.productreviews.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.loja.productreviews.domain.model.RatingAggregate;
import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.model.ReviewStatus;

/**
 * Persistence port for {@link Review}.
 *
 * <p>Implementations must enforce the unique
 * {@code (authorId, productId)} constraint at the database level
 * as a backstop for the domain check (see
 * {@link Review#submit(String, String, com.loja.productreviews.domain.model.Rating, String, String, boolean, boolean)}).
 */
public interface ReviewRepositoryPort {

    /** Persist a new or modified review; returns the same instance for chaining. */
    Review save(Review review);

    Optional<Review> findById(String reviewId);

    /** Approved reviews for a product, newest first, paginated. */
    List<Review> findApprovedByProduct(String productId, int page, int pageSize);

    long countApprovedByProduct(String productId);

    /** Pending reviews across all products, newest first, paginated. */
    List<Review> findByStatus(ReviewStatus status, int page, int pageSize);

    long countByStatus(ReviewStatus status);

    /** True iff the user already has a review for this product (any status). */
    boolean existsByUserAndProduct(String userId, String productId);

    /** Aggregate summary (count + average + histogram) for approved reviews only. */
    RatingAggregate aggregateApprovedByProduct(String productId);
}
