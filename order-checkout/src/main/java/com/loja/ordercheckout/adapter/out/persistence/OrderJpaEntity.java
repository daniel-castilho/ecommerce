package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderItem;
import com.loja.shared.domain.Money;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA persistence entity for Order.
 * Isolated in the adapter layer — the domain object (Order) never carries
 * framework annotations. Mapping is explicit via {@code fromDomain}/{@code toDomain},
 * following the UserJpaEntity/ProductJpaEntity pattern.
 */
@Entity
@Table(name = "tb_order")
public class OrderJpaEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Order.Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tb_order_item", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItemEmbeddable> items = new ArrayList<>();

    protected OrderJpaEntity() { }

    public static OrderJpaEntity fromDomain(Order order) {
        OrderJpaEntity e = new OrderJpaEntity();
        e.id = order.getId();
        e.userId = order.getUserId();
        e.status = order.getStatus();
        e.createdAt = order.getCreatedAt();
        e.items = order.getItems().stream()
                .map(OrderItemEmbeddable::fromDomain)
                .collect(Collectors.toList());
        return e;
    }

    public Order toDomain() {
        Order order = new Order(id, userId, createdAt);
        items.stream().map(OrderItemEmbeddable::toDomain).forEach(order::addItem);
        if (status == Order.Status.CONFIRMED) {
            order.confirm();
        } else if (status == Order.Status.CANCELLED) {
            order.cancel();
        }
        return order;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public Order.Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItemEmbeddable> getItems() { return items; }

    @Embeddable
    public static class OrderItemEmbeddable {

        @Column(name = "product_id", nullable = false, length = 36)
        private String productId;

        @Column(nullable = false)
        private int quantity;

        @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
        private BigDecimal unitPrice;

        protected OrderItemEmbeddable() { }

        public static OrderItemEmbeddable fromDomain(OrderItem item) {
            OrderItemEmbeddable e = new OrderItemEmbeddable();
            e.productId = item.getProductId();
            e.quantity = item.getQuantity();
            e.unitPrice = item.getUnitPrice().getAmount();
            return e;
        }

        public OrderItem toDomain() {
            return new OrderItem(productId, quantity, new Money(unitPrice));
        }

        public String getProductId() { return productId; }
        public int getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
    }
}
