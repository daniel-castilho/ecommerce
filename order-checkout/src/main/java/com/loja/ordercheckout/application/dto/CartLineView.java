package com.loja.ordercheckout.application.dto;

import com.loja.shared.domain.Money;
import java.util.Set;

/**
 * One row of the customer's cart enriched with live catalog data for display.
 * {@code available} is {@code false} when the product no longer exists or is not
 * ACTIVE — the UI still shows the row (so it can be removed) but with a fallback
 * label and no price. {@code categoryIds} feeds the coupon preview.
 */
public record CartLineView(
        String productId,
        String name,
        String slug,
        int quantity,
        Money unitPrice,
        Money lineTotal,
        String imageUrl,
        boolean available,
        Set<Long> categoryIds) {
}
