package com.loja.productcatalog.application.dto;

import java.util.Set;

import com.loja.shared.domain.Money;

/**
 * Inbound DTO for {@code UpdateProductUseCase}: only the fields editable after creation
 * (SKU and price are immutable on the {@code Product} domain object).
 */
public record UpdateProductCommand(
        String name,
        String slug,
        String shortDescription,
        String description,
        Money costPrice,
        int stock,
        Integer weightGrams,
        String metaTitle,
        String metaDescription,
        Set<Long> categoryIds) {
}
