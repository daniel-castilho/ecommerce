package com.loja.promotions.domain.port.in;

import com.loja.promotions.application.dto.DiscountLine;
import com.loja.promotions.application.dto.DiscountQuote;
import java.util.List;

/**
 * Checkout bridge: quotes the discount for a code against the cart lines
 * WITHOUT incrementing usage. Line-level data lets PRODUCT/CATEGORY-scoped
 * coupons discount only the eligible lines. Throws
 * {@code CouponNotFoundException} for unknown codes and
 * {@code CouponNotApplicableException} for inactive, out-of-window or exhausted
 * coupons.
 */
public interface QuoteDiscountUseCase {
    DiscountQuote quote(String code, List<DiscountLine> lines);
}
