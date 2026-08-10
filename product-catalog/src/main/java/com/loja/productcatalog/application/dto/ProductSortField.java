package com.loja.productcatalog.application.dto;

/**
 * Sort key for {@link ProductSearchCriteria}. {@link #RELEVANCE} ranks results
 * by PostgreSQL full-text match strength (used when a text term is present);
 * without a term it behaves like {@link #NAME} (alphabetical).
 */
public enum ProductSortField {
    RELEVANCE,
    NAME,
    PRICE,
    CREATED_AT
}
