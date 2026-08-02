package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.adapter.out.persistence.OrderJpaEntity.OrderItemEmbeddable;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderItem;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderJpaMapperTest {

    private Order newConfirmedOrder() {
        Order order = new Order("order-1", "user-1");
        order.addItem(new OrderItem("p1", 2, new Money(new BigDecimal("10.00"))));
        order.addItem(new OrderItem("p2", 3, new Money(new BigDecimal("5.50"))));
        order.confirm();
        return order;
    }

    @Test
    void shouldRoundTripConfirmedOrder() {
        Order original = newConfirmedOrder();

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getId()).isEqualTo("order-1");
        assertThat(restored.getUserId()).isEqualTo("user-1");
        assertThat(restored.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        assertThat(restored.getItems()).hasSize(2);
        assertThat(restored.getItems().get(0).getProductId()).isEqualTo("p1");
        assertThat(restored.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(restored.getItems().get(0).getUnitPrice()).isEqualTo(new Money(new BigDecimal("10.00")));
        assertThat(restored.getItems().get(1).getUnitPrice()).isEqualTo(new Money(new BigDecimal("5.50")));
        assertThat(restored.getTotal().getAmount()).isEqualByComparingTo("36.50");
    }

    @Test
    void shouldRoundTripOpenOrder() {
        Order original = new Order("order-2", "user-2");
        original.addItem(new OrderItem("p1", 1, new Money(new BigDecimal("7.25"))));

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getStatus()).isEqualTo(Order.Status.OPEN);
        assertThat(restored.getItems()).hasSize(1);
    }

    @Test
    void shouldRoundTripCancelledOrder() {
        Order original = new Order("order-3", "user-3");
        original.addItem(new OrderItem("p1", 1, new Money(new BigDecimal("7.25"))));
        original.cancel();

        Order restored = OrderJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(restored.getItems()).hasSize(1);
    }

    @Test
    void embeddableShouldRoundTripItem() {
        OrderItem item = new OrderItem("p9", 4, new Money(new BigDecimal("3.99")));

        OrderItem restored = OrderItemEmbeddable.fromDomain(item).toDomain();

        assertThat(restored.getProductId()).isEqualTo("p9");
        assertThat(restored.getQuantity()).isEqualTo(4);
        assertThat(restored.getUnitPrice()).isEqualTo(new Money(new BigDecimal("3.99")));
        assertThat(restored.getSubtotal().getAmount()).isEqualByComparingTo("15.96");
    }
}
