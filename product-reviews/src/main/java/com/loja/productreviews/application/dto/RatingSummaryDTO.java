package com.loja.productreviews.application.dto;

/**
 * Aggregate rating summary for a product.
 *
 * @param productId target product
 * @param count     number of approved reviews
 * @param average   average rating, rounded to 2 decimal places; {@code null} when there are no reviews
 * @param histogram length-5 array: {@code histogram[0]} = 1★, {@code histogram[4]} = 5★
 */
public record RatingSummaryDTO(String productId, long count, Double average, long[] histogram) {
}
