package com.loja.productcatalog.application.dto;

import java.util.List;

/** Generic paginated result for module queries. */
public record PageResult<T>(List<T> items, long totalElements, int page, int pageSize) {
    public int totalPages() {
        if (pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }
}
