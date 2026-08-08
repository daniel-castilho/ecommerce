package com.loja.wishlist.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public-facing representation of a wishlist entry enriched with product
 * display fields for the customer wishlist page.
 */
public record WishlistItemDTO(
        String id,
        String productId,
        String productName,
        String productSlug,
        BigDecimal price,
        String imageUrl,
        Instant createdAt) {
}
