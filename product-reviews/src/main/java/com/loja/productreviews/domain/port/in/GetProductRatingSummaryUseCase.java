package com.loja.productreviews.domain.port.in;

import com.loja.productreviews.application.dto.RatingSummaryDTO;

/**
 * Aggregate rating information for a product page.
 *
 * <p>Returns the count of approved reviews, their average rating
 * (rounded to two decimal places) and a 1..5 histogram.
 * The summary excludes pending, rejected and hidden reviews.
 */
public interface GetProductRatingSummaryUseCase {

    RatingSummaryDTO get(String productId);
}
