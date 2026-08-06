package com.loja.productreviews.domain.port.in;

import com.loja.productreviews.application.dto.SubmitReviewCommand;

/**
 * Customer submits a review for a product.
 *
 * <p><b>Given</b> an authenticated user id, a product id, a 1..5 rating and
 * optional title/body, when the user has not previously reviewed the product
 * and the product exists, then a new {@code Review} in PENDING state is
 * created and returned.
 *
 * <p>Throws {@link com.loja.productreviews.domain.exception.ProductNotFoundException}
 * if the product does not exist, and
 * {@link com.loja.productreviews.domain.exception.DuplicateReviewException}
 * if the user already has a review for this product.
 */
public interface SubmitReviewUseCase {

    /**
     * Submit a review.
     *
     * @param command review payload ({@link SubmitReviewCommand})
     * @return the persisted review's id
     */
    String submit(SubmitReviewCommand command);
}
