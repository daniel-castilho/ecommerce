package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;

/**
 * Immutable line item of an order. Captures a snapshot of the product at order
 * time (price may have changed since), so it does not reference the catalog.
 */
public final class OrderLine {

    private final String productId;
    private final String productName;
    private final Money unitPrice;
    private final int quantity;
    private final int position;

    public OrderLine(String productId, String productName, Money unitPrice, int quantity, int position) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product id is required");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("Unit price is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Position must not be negative");
        }
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.position = position;
    }

    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }

    /** EL-friendly alias of {@link #lineTotal()}. */
    public Money getLineTotal() {
        return lineTotal();
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Money getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public int getPosition() { return position; }
}
