package com.loja.productreviews.domain.port.in;

import com.loja.productreviews.application.dto.ReviewListPage;

/**
 * Admin-only paginated list of reviews currently in {@code PENDING} state,
 * awaiting moderation. Newest first.
 */
public interface ListPendingReviewsUseCase {

    ReviewListPage list(int page, int pageSize);
}
