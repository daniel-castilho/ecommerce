package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root of the order module.
 */
public class Order {

    public enum Status { OPEN, CONFIRMED, CANCELLED }

    private final String id;
    private final String userId;
    private final Instant createdAt;
    private final List<OrderItem> items = new ArrayList<>();
    private Status status;

    public Order(String id, String userId) {
        this(id, userId, Instant.now());
    }

    /** Persistence round-trip constructor (createdAt comes from the store). */
    public Order(String id, String userId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.status = Status.OPEN;
    }

    public void addItem(OrderItem item) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("Cannot modify an order with status " + status);
        }
        items.add(item);
    }

    public void confirm() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Order cannot be confirmed without items");
        }
        this.status = Status.CONFIRMED;
    }

    public void cancel() {
        if (status != Status.OPEN) {
            throw new IllegalStateException("Cannot cancel an order with status " + status);
        }
        this.status = Status.CANCELLED;
    }

    public Money getTotal() {
        return items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.zero(), Money::add);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
}
