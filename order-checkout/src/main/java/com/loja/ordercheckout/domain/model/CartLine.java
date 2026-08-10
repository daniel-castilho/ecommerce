package com.loja.ordercheckout.domain.model;

/**
 * A single product entry on a {@link Cart}: the product id and the requested
 * quantity. Immutable value object — {@link Cart} owns merging and removal.
 *
 * <p>Quantity is always {@code >= 1}. A quantity of zero means "remove the
 * line", which the cart expresses by dropping the line from its list. The unit
 * price is deliberately <b>not</b> stored here: prices are resolved live from
 * the catalog at read time so a price change is always reflected.
 */
public record CartLine(String productId, int quantity) {

    public CartLine {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        productId = productId.trim();
    }
}
