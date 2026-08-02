package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Money TEN = new Money(new BigDecimal("10.00"));
    private static final Money FIVE = new Money(new BigDecimal("5.00"));

    private Order newOpenOrder() {
        return new Order("order-1", "user-1");
    }

    private OrderItem item(String productId, int quantity, Money unitPrice) {
        return new OrderItem(productId, quantity, unitPrice);
    }

    @Test
    void constructor_newOrder_hasOpenStatusAndNoItems() {
        Order order = newOpenOrder();

        assertThat(order.getId()).isEqualTo("order-1");
        assertThat(order.getUserId()).isEqualTo("user-1");
        assertThat(order.getStatus()).isEqualTo(Order.Status.OPEN);
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void addItem_whenOpen_addsItem() {
        Order order = newOpenOrder();

        order.addItem(item("p1", 2, TEN));

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getProductId()).isEqualTo("p1");
    }

    @Test
    void addItem_whenNotOpen_throwsIllegalStateException() {
        Order order = newOpenOrder();
        order.addItem(item("p1", 1, TEN));
        order.confirm();

        assertThatThrownBy(() -> order.addItem(item("p2", 1, TEN)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void confirm_withEmptyItems_throwsIllegalStateException() {
        Order order = newOpenOrder();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without items");
    }

    @Test
    void confirm_withItems_setsStatusConfirmed() {
        Order order = newOpenOrder();
        order.addItem(item("p1", 1, TEN));

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
    }

    @Test
    void cancel_whenOpen_setsStatusCancelled() {
        Order order = newOpenOrder();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
    }

    @Test
    void cancel_whenNotOpen_throwsIllegalStateException() {
        Order order = newOpenOrder();
        order.addItem(item("p1", 1, TEN));
        order.confirm();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void getTotal_returnsSumOfSubtotals() {
        Order order = newOpenOrder();
        order.addItem(item("p1", 2, TEN));
        order.addItem(item("p2", 3, FIVE));

        assertThat(order.getTotal().getAmount()).isEqualByComparingTo("35.00");
    }

    @Test
    void getItems_returnsUnmodifiableList() {
        Order order = newOpenOrder();
        order.addItem(item("p1", 1, TEN));

        List<OrderItem> items = order.getItems();

        assertThatThrownBy(() -> items.add(item("p2", 1, TEN)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
