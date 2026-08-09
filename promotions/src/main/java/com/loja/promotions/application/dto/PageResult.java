package com.loja.promotions.application.dto;

import java.util.List;

/**
 * Generic paginated result for module queries.
 */
public record PageResult<T>(List<T> items, long totalElements, int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public int totalPages() {
        if (pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }
}
