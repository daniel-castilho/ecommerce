package com.loja.promotions.application.dto;

import com.loja.shared.domain.Money;
import java.util.Set;

/**
 * One cart line as seen by the pricing engine at quote time: the product, its
 * categories (for CATEGORY-scoped coupons) and its line total. The scope-aware
 * discount applies only to eligible lines, so the checkout must supply per-line
 * data instead of a single merchandise subtotal.
 *
 * @param productId    catalog product id
 * @param categoryIds  categories of the product at quote time (may be empty)
 * @param lineTotal    total price of this line (unit price x quantity)
 */
public record DiscountLine(String productId, Set<Long> categoryIds, Money lineTotal) {
}
