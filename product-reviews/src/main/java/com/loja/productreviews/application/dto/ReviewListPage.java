package com.loja.productreviews.application.dto;

import java.util.List;

/**
 * Paginated list of {@link ReviewDTO} for the admin moderation queue.
 *
 * <p>Lives in {@code application.dto} because it crosses the
 * {@link com.loja.productreviews.domain.port.in.ListPendingReviewsUseCase}
 * port — keeping it out of the port interface itself (lesson #7:
 * ArchUnit treats nested port records as a violation).
 */
public record ReviewListPage(List<ReviewDTO> items, long totalElements, int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public int totalPages() {
        if (pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }
}
