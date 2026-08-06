package com.loja.productreviews.application.dto;

import java.util.List;

/**
 * Generic paginated result, mirroring the convention of {@code product-catalog}
 * and {@code order-checkout}. Kept in this module so {@code product-reviews}
 * does not import a sibling module's DTO.
 */
public record PageResult<T>(List<T> items, long totalElements, int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    public int totalPages() {
        if (pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }
}
