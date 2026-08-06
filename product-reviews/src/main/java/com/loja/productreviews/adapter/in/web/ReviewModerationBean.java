package com.loja.productreviews.adapter.in.web;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.loja.productreviews.application.dto.ReviewDTO;
import com.loja.productreviews.application.dto.ReviewListPage;
import com.loja.productreviews.domain.port.in.ListPendingReviewsUseCase;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Admin moderation queue (spec §9/S9): lists PENDING reviews with pagination.
 * Moderation actions (approve/reject) live on {@link ReviewDetailBean}.
 */
@Named("reviewModerationBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class ReviewModerationBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 20;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Inject
    private transient ListPendingReviewsUseCase listPendingReviews;

    private ReviewListPage page = new ReviewListPage(List.of(), 0L, 0, PAGE_SIZE);
    private int pageIndex;

    void setListPendingReviews(ListPendingReviewsUseCase listPendingReviews) {
        this.listPendingReviews = listPendingReviews;
    }

    @PostConstruct
    void load() {
        refresh();
    }

    public List<ReviewDTO> getReviews() {
        return page.items();
    }

    public ReviewListPage getPage() {
        return page;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public long getTotalPages() {
        return page.totalPages();
    }

    public boolean isPreviousPageEnabled() {
        return pageIndex > 0;
    }

    public boolean isNextPageEnabled() {
        return pageIndex + 1 < page.totalPages();
    }

    public void refresh() {
        pageIndex = 0;
        page = listPendingReviews.list(pageIndex, PAGE_SIZE);
    }

    public void nextPage() {
        if (pageIndex + 1 < page.totalPages()) {
            pageIndex++;
            page = listPendingReviews.list(pageIndex, PAGE_SIZE);
        }
    }

    public void previousPage() {
        if (pageIndex > 0) {
            pageIndex--;
            page = listPendingReviews.list(pageIndex, PAGE_SIZE);
        }
    }

    public String formatDate(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }
}
