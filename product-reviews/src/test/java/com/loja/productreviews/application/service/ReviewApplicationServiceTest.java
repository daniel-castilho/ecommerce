package com.loja.productreviews.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.productreviews.application.dto.RatingSummaryDTO;
import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.application.dto.ReviewListPage;
import com.loja.productreviews.application.dto.SubmitReviewCommand;
import com.loja.productreviews.domain.exception.DuplicateReviewException;
import com.loja.productreviews.domain.exception.InvalidRatingException;
import com.loja.productreviews.domain.exception.ProductNotFoundException;
import com.loja.productreviews.domain.exception.ReviewAlreadyModeratedException;
import com.loja.productreviews.domain.exception.ReviewNotFoundException;
import com.loja.productreviews.domain.model.Rating;
import com.loja.productreviews.domain.model.RatingAggregate;
import com.loja.productreviews.domain.model.Review;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.port.in.HideOwnReviewUseCase;
import com.loja.productreviews.domain.port.out.OrderVerificationPort;
import com.loja.productreviews.domain.port.out.ProductLookupPort;
import com.loja.productreviews.domain.port.out.ReviewNotificationPort;
import com.loja.productreviews.domain.port.out.ReviewRepositoryPort;

class ReviewApplicationServiceTest {

    private final ReviewRepositoryPort reviewRepository = mock(ReviewRepositoryPort.class);
    private final ProductLookupPort productLookup = mock(ProductLookupPort.class);
    private final OrderVerificationPort orderVerification = mock(OrderVerificationPort.class);
    private final ReviewNotificationPort reviewNotification = mock(ReviewNotificationPort.class);

    private ReviewApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ReviewApplicationService(reviewRepository, productLookup, orderVerification,
                reviewNotification);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------- submit

    @Test
    void submit_withExistingProductAndNoPriorReview_persistsAndReturnsId() {
        when(productLookup.existsById("p-1")).thenReturn(true);
        when(reviewRepository.existsByUserAndProduct("u-1", "p-1")).thenReturn(false);
        when(orderVerification.hasUserPurchasedProduct("u-1", "p-1")).thenReturn(true);

        String id = service.submit(new SubmitReviewCommand("p-1", "u-1", 5, "Great", "Loved it"));

        assertThat(id).isNotBlank();
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void submit_withMissingProduct_throwsProductNotFound() {
        when(productLookup.existsById("p-x")).thenReturn(false);

        assertThatThrownBy(() -> service.submit(new SubmitReviewCommand("p-x", "u-1", 5, null, null)))
                .isInstanceOf(ProductNotFoundException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submit_withPriorReview_throwsDuplicate() {
        when(productLookup.existsById("p-1")).thenReturn(true);
        when(reviewRepository.existsByUserAndProduct("u-1", "p-1")).thenReturn(true);

        assertThatThrownBy(() -> service.submit(new SubmitReviewCommand("p-1", "u-1", 5, null, null)))
                .isInstanceOf(DuplicateReviewException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submit_withOutOfRangeRating_throwsInvalidRating() {
        when(productLookup.existsById("p-1")).thenReturn(true);
        when(reviewRepository.existsByUserAndProduct("u-1", "p-1")).thenReturn(false);

        assertThatThrownBy(() -> service.submit(new SubmitReviewCommand("p-1", "u-1", 6, null, null)))
                .isInstanceOf(InvalidRatingException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submit_sanitizesBodyAndTitle() {
        when(productLookup.existsById("p-1")).thenReturn(true);
        when(reviewRepository.existsByUserAndProduct("u-1", "p-1")).thenReturn(false);

        service.submit(new SubmitReviewCommand("p-1", "u-1", 4,
                "<b>ok</b>", "<script>alert(1)</script>hi"));

        org.mockito.ArgumentCaptor<Review> captor = org.mockito.ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        Review saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("<b>ok</b>");
        assertThat(saved.getBody()).doesNotContain("<script>").contains("hi");
    }

    @Test
    void submit_withoutPurchase_setsVerifiedPurchaseFalse() {
        when(productLookup.existsById("p-1")).thenReturn(true);
        when(reviewRepository.existsByUserAndProduct("u-1", "p-1")).thenReturn(false);
        when(orderVerification.hasUserPurchasedProduct("u-1", "p-1")).thenReturn(false);

        service.submit(new SubmitReviewCommand("p-1", "u-1", 3, null, null));

        org.mockito.ArgumentCaptor<Review> captor = org.mockito.ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().isVerifiedPurchase()).isFalse();
    }

    // -------------------------------------------------------------- list approved

    @Test
    void listApproved_returnsMappedDtos() {
        Review approved = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(5),
                "ok", "body", true, java.time.Instant.now(),
                ReviewStatus.APPROVED, java.time.Instant.now(), null);
        when(reviewRepository.findApprovedByProduct(eq("p-1"), anyInt(), anyInt()))
                .thenReturn(List.of(approved));

        List<ReviewDTO> dtos = service.list("p-1", 0, 10);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).id()).isEqualTo("r-1");
        assertThat(dtos.get(0).status()).isEqualTo(ReviewStatus.APPROVED);
    }

    // -------------------------------------------------------------- rating summary

    @Test
    void getSummary_roundsAverageToTwoDecimals() {
        when(reviewRepository.aggregateApprovedByProduct("p-1"))
                .thenReturn(new RatingAggregate(3L, 4.3333333, new long[]{0, 1, 0, 1, 1}));

        RatingSummaryDTO summary = service.get("p-1");

        assertThat(summary.count()).isEqualTo(3L);
        assertThat(summary.average()).isEqualTo(4.33);
        assertThat(summary.histogram()).containsExactly(0L, 1L, 0L, 1L, 1L);
    }

    @Test
    void getSummary_withNoReviews_returnsNullAverage() {
        when(reviewRepository.aggregateApprovedByProduct("p-1"))
                .thenReturn(new RatingAggregate(0L, null, new long[5]));

        RatingSummaryDTO summary = service.get("p-1");

        assertThat(summary.count()).isZero();
        assertThat(summary.average()).isNull();
    }

    // -------------------------------------------------------------- moderation

    @Test
    void listPending_returnsPagedDtos() {
        Review pending = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(4),
                null, null, false, java.time.Instant.now(),
                ReviewStatus.PENDING, null, null);
        when(reviewRepository.findByStatus(eq(ReviewStatus.PENDING), anyInt(), anyInt()))
                .thenReturn(List.of(pending));
        when(reviewRepository.countByStatus(ReviewStatus.PENDING)).thenReturn(1L);

        ReviewListPage page = service.list(0, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.page()).isZero();
    }

    @Test
    void approve_transitionsAndPersists() {
        Review pending = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(5),
                null, null, false, java.time.Instant.now(),
                ReviewStatus.PENDING, null, null);
        when(reviewRepository.findById("r-1")).thenReturn(Optional.of(pending));

        service.approve("r-1");

        assertThat(pending.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        verify(reviewRepository).save(pending);
    }

    @Test
    void approve_partiesApprovalNotificationToAuthor() {
        Review pending = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(5),
                null, null, false, java.time.Instant.now(),
                ReviewStatus.PENDING, null, null);
        when(reviewRepository.findById("r-1")).thenReturn(Optional.of(pending));

        service.approve("r-1");

        verify(reviewNotification).notifyApproved(pending);
    }

    @Test
    void approve_whenMissing_throwsNotFound() {
        when(reviewRepository.findById("r-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve("r-x"))
                .isInstanceOf(ReviewNotFoundException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void approve_whenAlreadyModerated_throwsReviewAlreadyModerated() {
        Review approved = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(5),
                null, null, false, java.time.Instant.now(),
                ReviewStatus.APPROVED, java.time.Instant.now(), null);
        when(reviewRepository.findById("r-1")).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approve("r-1"))
                .isInstanceOf(ReviewAlreadyModeratedException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void reject_setsReasonAndStatus() {
        Review pending = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(1),
                null, null, false, java.time.Instant.now(),
                ReviewStatus.PENDING, null, null);
        when(reviewRepository.findById("r-1")).thenReturn(Optional.of(pending));

        service.reject("r-1", "spam");

        assertThat(pending.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(pending.getRejectionReason()).isEqualTo("spam");
        verify(reviewRepository).save(pending);
    }

    @Test
    void reject_sendsRejectionNotificationToAuthor() {
        Review pending = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(1),
                null, null, false, java.time.Instant.now(),
                ReviewStatus.PENDING, null, null);
        when(reviewRepository.findById("r-1")).thenReturn(Optional.of(pending));

        service.reject("r-1", "spam");

        verify(reviewNotification).notifyRejected(pending, "spam");
    }

    // -------------------------------------------------------------- my reviews

    @Test
    void listMine_returnsOwnReviewsPaged() {
        Review approved = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(5),
                "ok", "body", true, java.time.Instant.now(),
                ReviewStatus.APPROVED, java.time.Instant.now(), null);
        when(reviewRepository.findByAuthor(eq("u-1"), anyInt(), anyInt()))
                .thenReturn(List.of(approved));
        when(reviewRepository.countByAuthor("u-1")).thenReturn(1L);

        ReviewListPage page = service.listMine("u-1", 0, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo("r-1");
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.page()).isZero();
    }

    @Test
    void listMine_clampsPageSize() {
        when(reviewRepository.findByAuthor(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(reviewRepository.countByAuthor("u-1")).thenReturn(0L);

        service.listMine("u-1", 0, 999);

        verify(reviewRepository).findByAuthor("u-1", 0, 100);
    }

    // -------------------------------------------------------------- hide

    @Test
    void hide_ownApprovedReview_succeeds() {
        Review approved = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(5),
                null, null, true, java.time.Instant.now(),
                ReviewStatus.APPROVED, java.time.Instant.now(), null);
        when(reviewRepository.findById("r-1")).thenReturn(Optional.of(approved));

        new HideOwnReviewUseCase() {
            @Override public void hide(String reviewId, String authorId) {
                service.hide(reviewId, authorId);
            }
        }.hide("r-1", "u-1");

        assertThat(approved.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    void hide_otherUsersReview_throws() {
        Review approved = Review.reconstitute("r-1", "p-1", "u-1", Rating.of(5),
                null, null, true, java.time.Instant.now(),
                ReviewStatus.APPROVED, java.time.Instant.now(), null);
        when(reviewRepository.findById("r-1")).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.hide("r-1", "someone-else"))
                .isInstanceOf(IllegalStateException.class);
        verify(reviewRepository, never()).save(any());
    }

    // -------------------------------------------------------------- page size guard

    @Test
    void list_clampsPageSizeAboveMax() {
        when(reviewRepository.findApprovedByProduct(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.list("p-1", 0, 999);

        verify(reviewRepository).findApprovedByProduct("p-1", 0, 100);
    }

    @Test
    void list_clampsPageSizeBelowOneToDefault() {
        when(reviewRepository.findApprovedByProduct(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.list("p-1", 0, 0);

        verify(reviewRepository).findApprovedByProduct("p-1", 0, 10);
    }
}
