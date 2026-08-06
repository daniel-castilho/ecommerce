package com.loja.productreviews.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.in.GetProductDetailUseCase;
import com.loja.productreviews.application.dto.RatingSummaryDTO;
import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.application.dto.SubmitReviewCommand;
import com.loja.productreviews.domain.exception.DuplicateReviewException;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.port.in.GetProductRatingSummaryUseCase;
import com.loja.productreviews.domain.port.in.ListApprovedReviewsByProductUseCase;
import com.loja.productreviews.domain.port.in.SubmitReviewUseCase;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import jakarta.faces.context.FacesContext;

class ProductReviewBeanTest {

    private static final String SLUG = "my-product";
    private static final String PRODUCT_ID = "p-1";

    private GetProductDetailUseCase getProductDetail;
    private ListApprovedReviewsByProductUseCase listApproved;
    private GetProductRatingSummaryUseCase ratingSummaryUseCase;
    private SubmitReviewUseCase submitReview;
    private SessionPort session;
    private ProductReviewBean bean;

    @BeforeEach
    void setUp() {
        getProductDetail = mock(GetProductDetailUseCase.class);
        listApproved = mock(ListApprovedReviewsByProductUseCase.class);
        ratingSummaryUseCase = mock(GetProductRatingSummaryUseCase.class);
        submitReview = mock(SubmitReviewUseCase.class);
        session = mock(SessionPort.class);
        bean = new ProductReviewBean();
        bean.setGetProductDetail(getProductDetail);
        bean.setListApproved(listApproved);
        bean.setRatingSummaryUseCase(ratingSummaryUseCase);
        bean.setSubmitReview(submitReview);
        bean.setSession(session);
    }

    @Test
    void load_knownSlug_loadsSummaryReviewsAndPaging() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(getProductDetail.findActiveBySlug(new Slug(SLUG))).thenReturn(Optional.of(product));
        RatingSummaryDTO summary = new RatingSummaryDTO(PRODUCT_ID, 12L, 4.5,
                new long[]{0, 0, 1, 3, 8});
        when(ratingSummaryUseCase.get(PRODUCT_ID)).thenReturn(summary);
        when(listApproved.list(PRODUCT_ID, 0, 10))
                .thenReturn(List.of(reviewDTO("r-1"), reviewDTO("r-2")));

        bean.load(SLUG, 0);

        assertThat(bean.isReviewableProduct()).isTrue();
        assertThat(bean.getSummary()).isSameAs(summary);
        assertThat(bean.getReviews()).hasSize(2);
        assertThat(bean.getTotalPages()).isEqualTo(2);
        assertThat(bean.getAverageStars()).isEqualTo(5);
        assertThat(bean.getHistogramBars()).hasSize(5);
    }

    @Test
    void load_unknownSlug_notReviewableAndNoReviews() {
        when(getProductDetail.findActiveBySlug(new Slug(SLUG))).thenReturn(Optional.empty());

        bean.load(SLUG, 0);

        assertThat(bean.isReviewableProduct()).isFalse();
        assertThat(bean.getReviews()).isEmpty();
    }

    @Test
    void load_blankSlug_notReviewable() {
        bean.load("  ", 0);

        assertThat(bean.isReviewableProduct()).isFalse();
        verify(getProductDetail, never()).findActiveBySlug(Mockito.any());
    }

    @Test
    void submit_loggedIn_delegatesAndResetsForm() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(getProductDetail.findActiveBySlug(new Slug(SLUG))).thenReturn(Optional.of(product));
        when(ratingSummaryUseCase.get(PRODUCT_ID))
                .thenReturn(new RatingSummaryDTO(PRODUCT_ID, 0L, null, new long[5]));
        bean.load(SLUG, 0);

        User user = mock(User.class);
        when(user.getId()).thenReturn("u-1");
        when(session.getCurrentUser()).thenReturn(Optional.of(user));

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));
            bean.setRating(4);
            bean.setTitle("Great");
            bean.setBody("Loved it");

            bean.submit();

            verify(submitReview).submit(new SubmitReviewCommand(
                    PRODUCT_ID, "u-1", 4, "Great", "Loved it"));
            assertThat(bean.getRating()).isZero();
            assertThat(bean.getTitle()).isNull();
            assertThat(bean.getBody()).isNull();
        }
    }

    @Test
    void submit_notLoggedIn_doesNotDelegate() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));
            bean.setRating(5);

            bean.submit();

            verify(submitReview, never()).submit(Mockito.any());
        }
    }

    @Test
    void submit_duplicateReview_swallowsExceptionAndAddsMessage() {
        when(session.getCurrentUser()).thenReturn(Optional.of(mock(User.class)));
        when(submitReview.submit(Mockito.any())).thenThrow(new DuplicateReviewException("u-1", PRODUCT_ID));

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            FacesContext context = mock(FacesContext.class);
            faces.when(FacesContext::getCurrentInstance).thenReturn(context);
            bean.setRating(3);

            bean.submit();

            verify(submitReview).submit(Mockito.any());
            verify(context).addMessage(Mockito.isNull(), Mockito.any());
        }
    }

    private static ReviewDTO reviewDTO(String id) {
        return new ReviewDTO(id, PRODUCT_ID, "u-1", 5, "Great", "Loved it", true,
                ReviewStatus.APPROVED, Instant.parse("2026-08-01T10:00:00Z"), null, null);
    }
}
