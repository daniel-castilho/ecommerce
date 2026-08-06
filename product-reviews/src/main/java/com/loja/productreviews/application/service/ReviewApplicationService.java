package com.loja.productreviews.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.loja.productreviews.application.dto.PageResult;
import com.loja.productreviews.application.dto.RatingSummaryDTO;
import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.application.dto.ReviewListPage;
import com.loja.productreviews.application.dto.SubmitReviewCommand;
import com.loja.productreviews.domain.exception.ProductNotFoundException;
import com.loja.productreviews.domain.exception.ReviewNotFoundException;
import com.loja.productreviews.domain.model.Rating;
import com.loja.productreviews.domain.model.RatingAggregate;
import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.port.in.ApproveReviewUseCase;
import com.loja.productreviews.domain.port.in.GetProductRatingSummaryUseCase;
import com.loja.productreviews.domain.port.in.GetReviewByIdUseCase;
import com.loja.productreviews.domain.port.in.HideOwnReviewUseCase;
import com.loja.productreviews.domain.port.in.ListApprovedReviewsByProductUseCase;
import com.loja.productreviews.domain.port.in.ListPendingReviewsUseCase;
import com.loja.productreviews.domain.port.in.RejectReviewUseCase;
import com.loja.productreviews.domain.port.in.SubmitReviewUseCase;
import com.loja.productreviews.domain.port.out.OrderVerificationPort;
import com.loja.productreviews.domain.port.out.ProductLookupPort;
import com.loja.productreviews.domain.port.out.ReviewRepositoryPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Application service implementing every review-related use case (spec §5).
 *
 * <p>Business rules that only depend on the {@link Review} aggregate itself
 * live on the aggregate (state transitions, validation). Rules that must
 * consult a port — product existence, verified-purchase lookup, persistence —
 * live here.
 */
@ApplicationScoped
@Transactional
public class ReviewApplicationService implements
        SubmitReviewUseCase,
        ListApprovedReviewsByProductUseCase,
        GetProductRatingSummaryUseCase,
        GetReviewByIdUseCase,
        ListPendingReviewsUseCase,
        ApproveReviewUseCase,
        RejectReviewUseCase,
        HideOwnReviewUseCase {

    private static final PolicyFactory BODY_SANITIZER =
            Sanitizers.FORMATTING.and(Sanitizers.BLOCKS).and(Sanitizers.LINKS);

    private final ReviewRepositoryPort reviewRepository;
    private final ProductLookupPort productLookup;
    private final OrderVerificationPort orderVerification;

    @Inject
    public ReviewApplicationService(ReviewRepositoryPort reviewRepository,
                                    ProductLookupPort productLookup,
                                    OrderVerificationPort orderVerification) {
        this.reviewRepository = reviewRepository;
        this.productLookup = productLookup;
        this.orderVerification = orderVerification;
    }

    @Override
    public String submit(SubmitReviewCommand command) {
        Objects.requireNonNull(command, "command");
        if (!productLookup.existsById(command.productId())) {
            throw new ProductNotFoundException(command.productId());
        }

        boolean alreadyReviewed = reviewRepository.existsByUserAndProduct(
                command.authorId(), command.productId());

        boolean verified = orderVerification.hasUserPurchasedProduct(
                command.authorId(), command.productId());

        Rating rating = Rating.of(command.rating());
        String sanitizedBody = sanitize(command.body());
        String sanitizedTitle = sanitize(command.title());

        Review review = Review.submit(
                command.productId(),
                command.authorId(),
                rating,
                sanitizedTitle,
                sanitizedBody,
                verified,
                alreadyReviewed);

        return reviewRepository.save(review).getId();
    }

    @Override
    public List<ReviewDTO> list(String productId, int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safeSize = clampPageSize(pageSize);
        List<Review> reviews = reviewRepository.findApprovedByProduct(productId, safePage, safeSize);
        return reviews.stream().map(ReviewDTO::from).toList();
    }

    @Override
    public long countApproved(String productId) {
        return reviewRepository.countApprovedByProduct(productId);
    }

    @Override
    public RatingSummaryDTO get(String productId) {
        RatingAggregate aggregate =
                reviewRepository.aggregateApprovedByProduct(productId);        Double roundedAverage = null;
        if (aggregate.average() != null) {
            roundedAverage = BigDecimal.valueOf(aggregate.average())
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        long[] histogram = aggregate.histogram() != null ? aggregate.histogram() : new long[5];
        return new RatingSummaryDTO(productId, aggregate.count(), roundedAverage, histogram);
    }

    @Override
    public Optional<ReviewDTO> findById(String reviewId) {
        return reviewRepository.findById(reviewId).map(ReviewDTO::from);
    }

    @Override
    public ReviewListPage list(int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safeSize = clampPageSize(pageSize);
        List<Review> pending = reviewRepository.findByStatus(ReviewStatus.PENDING, safePage, safeSize);
        long total = reviewRepository.countByStatus(ReviewStatus.PENDING);
        List<ReviewDTO> dtos = pending.stream().map(ReviewDTO::from).toList();
        return new ReviewListPage(dtos, total, safePage, safeSize);
    }

    @Override
    public void approve(String reviewId) {
        Review review = loadOrThrow(reviewId);
        review.approve();
        reviewRepository.save(review);
    }

    @Override
    public void reject(String reviewId, String reason) {
        Review review = loadOrThrow(reviewId);
        review.reject(reason);
        reviewRepository.save(review);
    }

    @Override
    public void hide(String reviewId, String authorId) {
        Review review = loadOrThrow(reviewId);
        if (!review.getAuthorId().equals(authorId)) {
            throw new IllegalStateException("Only the author can hide their own review");
        }
        review.hide();
        reviewRepository.save(review);
    }

    /**
     * Variant of {@link #list(String, int, int)} that returns a paginated result
     * for callers (e.g. the JSF bean) that want the total count. Kept package-private
     * by returning the module-local {@link PageResult} type.
     */
    public PageResult<ReviewDTO> listPaged(String productId, int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safeSize = clampPageSize(pageSize);
        List<ReviewDTO> items = list(productId, safePage, safeSize);
        long total = reviewRepository.countApprovedByProduct(productId);
        return new PageResult<>(items, total, safePage, safeSize);
    }

    private Review loadOrThrow(String reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
    }

    private static int clampPageSize(int pageSize) {
        if (pageSize <= 0) {
            return PageResult.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, PageResult.MAX_PAGE_SIZE);
    }

    private static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return BODY_SANITIZER.sanitize(input);
    }
}
