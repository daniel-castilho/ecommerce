package com.loja.productcatalog.application.dto;

import com.loja.productcatalog.domain.model.ProductStatus;
import java.math.BigDecimal;

/**
 * Search criteria for paginated product queries (spec §4). Every filter field is
 * optional — {@code null}/{@code blank} means "no filter". {@code page} is 0-based;
 * {@code pageSize} defaults to {@link #DEFAULT_PAGE_SIZE} and is hard-capped at
 * {@link #MAX_PAGE_SIZE}. A {@code null} status means "exclude ARCHIVED unless the
 * caller explicitly requests it".
 */
public record ProductSearchCriteria(
        String nameOrSkuContains,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatus status,
        int page,
        int pageSize,
        ProductSortField sortField,
        SortDirection sortDirection) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public ProductSearchCriteria {
        page = Math.max(page, 0);
        pageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        sortField = sortField != null ? sortField : ProductSortField.NAME;
        sortDirection = sortDirection != null ? sortDirection : SortDirection.ASC;
    }
}
