package com.loja.ordercheckout.domain.model;

import java.time.Instant;

/**
 * Filter and paging criteria for the admin order list. Nullable members mean
 * "no filter". Sorting is whitelisted by the repository to the {@code sortBy}
 * columns supported for admin search.
 */
public record OrderSearchCriteria(OrderStatus status,
                                  String searchTerm,
                                  Instant createdFrom,
                                  Instant createdTo,
                                  String sortBy,
                                  boolean ascending,
                                  int page,
                                  int pageSize) {

    public static final String SORT_CREATED_AT = "createdAt";
    public static final String SORT_CUSTOMER_EMAIL = "customerEmail";
    public static final String SORT_STATUS = "status";

    public OrderSearchCriteria {
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = SORT_CREATED_AT;
        }
        page = Math.max(page, 0);
        pageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
    }

    public String normalizedSearchTerm() {
        return searchTerm == null ? null : searchTerm.trim().toLowerCase();
    }
}
