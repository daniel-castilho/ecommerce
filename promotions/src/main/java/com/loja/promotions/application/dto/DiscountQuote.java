package com.loja.promotions.application.dto;

import com.loja.shared.domain.Money;

/**
 * Result of quoting a coupon against a merchandise subtotal. Usage is NOT
 * incremented by a quote; redemption is a separate step.
 *
 * @param code           normalized coupon code
 * @param discountAmount discount to apply to the merchandise subtotal
 */
public record DiscountQuote(String code, Money discountAmount) { }
