package com.loja.productreviews.domain.model;

/**
 * Aggregate rating information for a product (repository-level query result).
 *
 * @param count     number of approved reviews
 * @param average   average rating, or {@code null} when there are no reviews
 * @param histogram length-5 array: {@code histogram[0]} = 1★, {@code histogram[4]} = 5★
 */
public record RatingAggregate(long count, Double average, long[] histogram) { }
