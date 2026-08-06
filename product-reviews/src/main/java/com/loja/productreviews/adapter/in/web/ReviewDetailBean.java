package com.loja.productreviews.adapter.in.web;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.domain.exception.ReviewAlreadyModeratedException;
import com.loja.productreviews.domain.model.ReviewStatus;
import com.loja.productreviews.domain.port.in.ApproveReviewUseCase;
import com.loja.productreviews.domain.port.in.GetReviewByIdUseCase;
import com.loja.productreviews.domain.port.in.RejectReviewUseCase;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Admin review moderation detail (spec §10/§11, stories S10/S11): approve or
 * reject a pending review with a mandatory reason.
 */
@Named("reviewDetailBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class ReviewDetailBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Inject
    private transient GetReviewByIdUseCase getReviewById;

    @Inject
    private transient ApproveReviewUseCase approveReview;

    @Inject
    private transient RejectReviewUseCase rejectReview;

    private ReviewDTO selectedReview;
    private String reviewId;
    private String rejectionReason;

    void setGetReviewById(GetReviewByIdUseCase getReviewById) {
        this.getReviewById = getReviewById;
    }

    void setApproveReview(ApproveReviewUseCase approveReview) {
        this.approveReview = approveReview;
    }

    void setRejectReview(RejectReviewUseCase rejectReview) {
        this.rejectReview = rejectReview;
    }

    @PostConstruct
    void init() {
        reviewId = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap().get("reviewId");
        loadReview(reviewId);
    }

    public void loadReview(String id) {
        reviewId = id;
        selectedReview = id == null || id.isBlank()
                ? null
                : getReviewById.findById(id).orElse(null);
    }

    public void approve() {
        if (selectedReview == null) {
            return;
        }
        try {
            approveReview.approve(selectedReview.id());
            addMessage(FacesMessage.SEVERITY_INFO, "Review approved",
                    "The review is now visible on the product page.");
        } catch (ReviewAlreadyModeratedException e) {
            addMessage(FacesMessage.SEVERITY_WARN, "Already moderated",
                    "This review has already been moderated.");
        }
        loadReview(reviewId);
    }

    public void reject() {
        if (selectedReview == null) {
            return;
        }
        if (rejectionReason == null || rejectionReason.isBlank()) {
            addMessage(FacesMessage.SEVERITY_WARN, "Rejection reason required",
                    "Provide a reason before rejecting the review.");
            return;
        }
        try {
            rejectReview.reject(selectedReview.id(), rejectionReason);
            addMessage(FacesMessage.SEVERITY_INFO, "Review rejected",
                    "The review was rejected.");
        } catch (ReviewAlreadyModeratedException e) {
            addMessage(FacesMessage.SEVERITY_WARN, "Already moderated",
                    "This review has already been moderated.");
        }
        loadReview(reviewId);
    }

    public boolean isPending() {
        return selectedReview != null && selectedReview.status() == ReviewStatus.PENDING;
    }

    public String formatDate(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    public ReviewDTO getSelectedReview() {
        return selectedReview;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    private void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, summary, detail));
    }
}
