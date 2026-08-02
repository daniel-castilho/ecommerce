package com.loja.productcatalog.application.dto;

import com.loja.shared.domain.Money;
import java.util.Set;

/** Inbound DTO for {@code CreateProductUseCase}: all fields the admin supplies at creation. */
public record CreateProductCommand(
        String sku,
        String name,
        String slug,
        String shortDescription,
        String description,
        Money price,
        Money compareAtPrice,
        int stock,
        Integer weightGrams,
        String metaTitle,
        String metaDescription,
        Set<Long> categoryIds) {
}
