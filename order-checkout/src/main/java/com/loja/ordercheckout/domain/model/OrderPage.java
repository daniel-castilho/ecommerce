package com.loja.ordercheckout.domain.model;

import java.util.List;

/**
 * Paginated admin search result over orders. Lives in the domain so that the
 * admin-dashboard module can consume it through the use-case port without
 * importing an application-layer DTO.
 */
public record OrderPage(List<Order> items, long totalElements, int page, int pageSize) {

    public int totalPages() {
        if (pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }
}
