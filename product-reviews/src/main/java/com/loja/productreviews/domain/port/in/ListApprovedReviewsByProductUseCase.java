package com.loja.productreviews.domain.port.in;

import java.util.List;

import com.loja.productreviews.application.dto.ReviewDTO;

/**
 * Public, paginated list of {@code APPROVED} reviews for a product.
 *
 * <p>Hidden reviews are not returned; pending/rejected reviews are visible
 * only to admins through {@link ListPendingReviewsUseCase}.
 */
public interface ListApprovedReviewsByProductUseCase {

    /**
     * @param productId target product
     * @param page      zero-based page index
     * @param pageSize  page size (1..100)
     * @return page of approved reviews, newest first
     */
    List<ReviewDTO> list(String productId, int page, int pageSize);

    /**
     * @param productId target product
     * @return total number of approved reviews for the product (for pagination)
     */
    long countApproved(String productId);
}
