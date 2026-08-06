package com.loja.productreviews.domain.port.in;

import java.util.Optional;

import com.loja.productreviews.application.dto.ReviewDTO;

/**
 * Single review lookup for the admin moderation detail page.
 */
public interface GetReviewByIdUseCase {

    /**
     * @param reviewId target review id
     * @return the review as a DTO, or empty if no review has this id
     */
    Optional<ReviewDTO> findById(String reviewId);
}
