package com.loja.productreviews.domain.port.in;

import com.loja.productreviews.application.dto.ReviewListPage;

/**
 * Returns the caller's own reviews across all states (PENDING, APPROVED,
 * REJECTED, HIDDEN), newest first, paginated — the "my reviews" screen.
 */
public interface ListMyReviewsUseCase {

    ReviewListPage listMine(String authorId, int page, int pageSize);
}