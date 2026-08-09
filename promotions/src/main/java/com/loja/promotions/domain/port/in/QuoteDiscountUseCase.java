package com.loja.promotions.domain.port.in;

import com.loja.promotions.application.dto.DiscountQuote;
import com.loja.shared.domain.Money;

/**
 * Checkout bridge: quotes the discount for a code against a merchandise
 * subtotal WITHOUT incrementing usage. Throws {@code CouponNotFoundException}
 * for unknown codes and {@code CouponNotApplicableException} for inactive,
 * out-of-window or exhausted coupons.
 */
public interface QuoteDiscountUseCase {
    DiscountQuote quote(String code, Money merchandiseSubtotal);
}
