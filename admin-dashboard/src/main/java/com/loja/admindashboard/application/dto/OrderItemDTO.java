package com.loja.admindashboard.application.dto;

import java.util.Objects;

import com.loja.shared.domain.Money;

/**
 * Presentation-friendly order item used by admin order details.
 */
public final class OrderItemDTO {

    private final String productName;
    private final Money unitPrice;
    private final int quantity;
    private final Money lineTotal;

    public OrderItemDTO(String productName, Money unitPrice, int quantity, Money lineTotal) {
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
    }

    public String getProductName() {
        return productName;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getLineTotal() {
        return lineTotal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItemDTO that = (OrderItemDTO) o;
        return quantity == that.quantity
                && Objects.equals(productName, that.productName)
                && Objects.equals(unitPrice, that.unitPrice)
                && Objects.equals(lineTotal, that.lineTotal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productName, unitPrice, quantity, lineTotal);
    }
}
