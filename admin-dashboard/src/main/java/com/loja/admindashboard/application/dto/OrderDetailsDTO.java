package com.loja.admindashboard.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.shared.domain.Money;

/**
 * Presentation-friendly DTO for admin order detail views.
 */
public final class OrderDetailsDTO {

    private final String id;
    private final OrderStatus status;
    private final String customerEmail;
    private final Instant createdAt;
    private final ShippingAddressDTO shippingAddress;
    private final List<OrderItemDTO> items;
    private final Money shippingCost;
    private final Money total;
    private final String trackingNumber;

    public OrderDetailsDTO(
            String id,
            OrderStatus status,
            String customerEmail,
            Instant createdAt,
            ShippingAddressDTO shippingAddress,
            List<OrderItemDTO> items,
            Money shippingCost,
            Money total,
            String trackingNumber) {
        this.id = id;
        this.status = status;
        this.customerEmail = customerEmail;
        this.createdAt = createdAt;
        this.shippingAddress = shippingAddress;
        this.items = items;
        this.shippingCost = shippingCost;
        this.total = total;
        this.trackingNumber = trackingNumber;
    }

    public String getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ShippingAddressDTO getShippingAddress() {
        return shippingAddress;
    }

    public List<OrderItemDTO> getItems() {
        return items;
    }

    public Money getShippingCost() {
        return shippingCost;
    }

    public Money getTotal() {
        return total;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderDetailsDTO that = (OrderDetailsDTO) o;
        return Objects.equals(id, that.id)
                && status == that.status
                && Objects.equals(customerEmail, that.customerEmail)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(shippingAddress, that.shippingAddress)
                && Objects.equals(items, that.items)
                && Objects.equals(shippingCost, that.shippingCost)
                && Objects.equals(total, that.total)
                && Objects.equals(trackingNumber, that.trackingNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, customerEmail, createdAt, shippingAddress, items, shippingCost, total, trackingNumber);
    }
}
