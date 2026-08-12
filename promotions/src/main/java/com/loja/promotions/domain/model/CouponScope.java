package com.loja.promotions.domain.model;

/**
 * Eligibility scope of a {@link Coupon}: apply to the whole order, only to
 * specific products, or only to products in specific categories. {@code ALL}
 * is the default and matches the original whole-order behavior.
 */
public enum CouponScope {
    ALL,
    PRODUCT,
    CATEGORY
}
