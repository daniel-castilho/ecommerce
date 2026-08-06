package com.loja.productreviews.adapter.out.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.productreviews.domain.port.out.OrderVerificationPort;
import com.loja.shared.domain.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class OrderVerificationAdapterTest {

    private static final String USER = "user-1";
    private static final String PRODUCT = "product-1";

    private OrderRepositoryPort orderRepository;
    private OrderVerificationPort adapter;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepositoryPort.class);
        adapter = new OrderVerificationAdapter(orderRepository);
    }

    @Test
    void hasUserPurchasedProduct_orderConfirmedWithProduct_returnsTrue() {
        when(orderRepository.findByCustomerId(eq(USER), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(
                        order(OrderStatus.CONFIRMED, PRODUCT), order(OrderStatus.PENDING, "other")),
                        2, 0, PageResult.MAX_PAGE_SIZE));

        assertThat(adapter.hasUserPurchasedProduct(USER, PRODUCT)).isTrue();
    }

    @Test
    void hasUserPurchasedProduct_orderShippedOrDeliveredWithProduct_returnsTrue() {
        when(orderRepository.findByCustomerId(eq(USER), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(
                        order(OrderStatus.SHIPPED, PRODUCT), order(OrderStatus.DELIVERED, PRODUCT)),
                        2, 0, PageResult.MAX_PAGE_SIZE));

        assertThat(adapter.hasUserPurchasedProduct(USER, PRODUCT)).isTrue();
    }

    @Test
    void hasUserPurchasedProduct_orderCancelledOrRefunded_returnsFalse() {
        when(orderRepository.findByCustomerId(eq(USER), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(
                        order(OrderStatus.CANCELLED, PRODUCT), order(OrderStatus.REFUNDED, PRODUCT)),
                        2, 0, PageResult.MAX_PAGE_SIZE));

        assertThat(adapter.hasUserPurchasedProduct(USER, PRODUCT)).isFalse();
    }

    @Test
    void hasUserPurchasedProduct_pendingOrderOnly_returnsFalse() {
        when(orderRepository.findByCustomerId(eq(USER), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(order(OrderStatus.PENDING, PRODUCT)),
                        1, 0, PageResult.MAX_PAGE_SIZE));

        assertThat(adapter.hasUserPurchasedProduct(USER, PRODUCT)).isFalse();
    }

    @Test
    void hasUserPurchasedProduct_orderWithoutProduct_returnsFalse() {
        when(orderRepository.findByCustomerId(eq(USER), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(order(OrderStatus.DELIVERED, "other")),
                        1, 0, PageResult.MAX_PAGE_SIZE));

        assertThat(adapter.hasUserPurchasedProduct(USER, PRODUCT)).isFalse();
    }

    @Test
    void hasUserPurchasedProduct_noOrders_returnsFalse() {
        when(orderRepository.findByCustomerId(eq(USER), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 0, PageResult.MAX_PAGE_SIZE));

        assertThat(adapter.hasUserPurchasedProduct(USER, PRODUCT)).isFalse();
    }

    @Test
    void hasUserPurchasedProduct_productOnLaterPage_returnsTrue() {
        when(orderRepository.findByCustomerId(eq(USER), eq(0), anyInt()))
                .thenReturn(new PageResult<>(List.of(order(OrderStatus.DELIVERED, "other")),
                        150, 0, PageResult.MAX_PAGE_SIZE));
        when(orderRepository.findByCustomerId(eq(USER), eq(1), anyInt()))
                .thenReturn(new PageResult<>(List.of(order(OrderStatus.CONFIRMED, PRODUCT)),
                        150, 1, PageResult.MAX_PAGE_SIZE));

        assertThat(adapter.hasUserPurchasedProduct(USER, PRODUCT)).isTrue();
    }

    @Test
    void hasUserPurchasedProduct_matchNeverAppears_scansAllPagesAndReturnsFalse() {
        when(orderRepository.findByCustomerId(eq(USER), eq(0), anyInt()))
                .thenReturn(new PageResult<>(List.of(order(OrderStatus.DELIVERED, "other")),
                        150, 0, PageResult.MAX_PAGE_SIZE));
        when(orderRepository.findByCustomerId(eq(USER), eq(1), anyInt()))
                .thenReturn(new PageResult<>(List.of(order(OrderStatus.DELIVERED, "other")),
                        150, 1, PageResult.MAX_PAGE_SIZE));
        when(orderRepository.findByCustomerId(eq(USER), eq(2), anyInt()))
                .thenReturn(new PageResult<>(List.of(order(OrderStatus.DELIVERED, "other")),
                        150, 2, PageResult.MAX_PAGE_SIZE));

        assertThat(adapter.hasUserPurchasedProduct(USER, PRODUCT)).isFalse();
    }

    private static Order order(OrderStatus status, String productId) {
        OrderLine line = new OrderLine(productId, "Product", Money.zero(), 1, 0);
        ShippingAddress address = new ShippingAddress("Jane Doe", "Main St", "1", null,
                "Downtown", "Sao Paulo", "SP", "12345-678", null);
        Instant now = Instant.now();
        return Order.restore("order-" + status + "-" + productId, USER, "jane@example.com", now,
                status, List.of(line), address, Money.zero(), null, null, now, 0L);
    }
}
