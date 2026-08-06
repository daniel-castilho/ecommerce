package com.loja.productreviews.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.port.in.ApproveReviewUseCase;
import com.loja.productreviews.domain.port.in.GetReviewByIdUseCase;
import com.loja.productreviews.domain.port.in.RejectReviewUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import jakarta.faces.context.FacesContext;

class ReviewDetailBeanTest {

    private GetReviewByIdUseCase getReviewById;
    private ApproveReviewUseCase approveReview;
    private RejectReviewUseCase rejectReview;
    private ReviewDetailBean bean;

    @BeforeEach
    void setUp() {
        getReviewById = mock(GetReviewByIdUseCase.class);
        approveReview = mock(ApproveReviewUseCase.class);
        rejectReview = mock(RejectReviewUseCase.class);
        bean = new ReviewDetailBean();
        bean.setGetReviewById(getReviewById);
        bean.setApproveReview(approveReview);
        bean.setRejectReview(rejectReview);
    }

    @Test
    void loadReview_knownId_loadsSelectedAndPending() {
        when(getReviewById.findById("r-1")).thenReturn(Optional.of(pendingReview()));

        bean.loadReview("r-1");

        assertThat(bean.getSelectedReview()).isNotNull();
        assertThat(bean.isPending()).isTrue();
    }

    @Test
    void loadReview_unknownId_selectedIsNull() {
        when(getReviewById.findById("r-1")).thenReturn(Optional.empty());

        bean.loadReview("r-1");

        assertThat(bean.getSelectedReview()).isNull();
        assertThat(bean.isPending()).isFalse();
    }

    @Test
    void approve_delegatesAndReloads() {
        when(getReviewById.findById("r-1")).thenReturn(Optional.of(pendingReview()));
        bean.loadReview("r-1");

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.approve();

            verify(approveReview).approve("r-1");
            verify(getReviewById, Mockito.times(2)).findById("r-1");
        }
    }

    @Test
    void reject_withReason_delegatesAndReloads() {
        when(getReviewById.findById("r-1")).thenReturn(Optional.of(pendingReview()));
        bean.loadReview("r-1");

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));
            bean.setRejectionReason("Spam");

            bean.reject();

            verify(rejectReview).reject("r-1", "Spam");
        }
    }

    @Test
    void reject_withoutReason_doesNotDelegate() {
        when(getReviewById.findById("r-1")).thenReturn(Optional.of(pendingReview()));
        bean.loadReview("r-1");

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.reject();

            verify(rejectReview, never()).reject(Mockito.any(), Mockito.any());
        }
    }

    private static ReviewDTO pendingReview() {
        return new ReviewDTO("r-1", "p-1", "u-1", 4, "Nice", "Works well", true,
                ReviewStatus.PENDING, Instant.parse("2026-08-01T10:00:00Z"), null, null);
    }
}
