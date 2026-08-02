package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.port.in.CheckoutUseCase.ItemCheckoutRequest;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.productcatalog.domain.exception.InsufficientStockException;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckoutServiceTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final ProductRepositoryPort productRepository = mock(ProductRepositoryPort.class);

    private CheckoutService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutService(orderRepository, productRepository);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product product(String id, Money price, int stock) {
        return new Product(id, new Sku("SKU-" + id), new Slug("slug-" + id), "Product " + id,
                null, null, price, null, stock, ProductStatus.ACTIVE,
                null, null, null, Set.of(1L), List.of());
    }

    @Test
    void checkout_withValidItems_createsConfirmedOrderAndSaves() {
        when(productRepository.findById("p1")).thenReturn(Optional.of(product("p1", new Money(new BigDecimal("10.00")), 5)));
        when(productRepository.findById("p2")).thenReturn(Optional.of(product("p2", new Money(new BigDecimal("5.00")), 3)));
        when(productRepository.decrementStock(anyString(), anyInt())).thenReturn(1);

        Order order = service.checkout("user-1", List.of(
                new ItemCheckoutRequest("p1", 2),
                new ItemCheckoutRequest("p2", 3)));

        assertThat(order.getUserId()).isEqualTo("user-1");
        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotal().getAmount()).isEqualByComparingTo("35.00");
        verify(productRepository).decrementStock("p1", 2);
        verify(productRepository).decrementStock("p2", 3);
        verify(orderRepository).save(order);
    }

    @Test
    void checkout_withZeroQuantity_throwsIllegalArgumentException() {
        when(productRepository.findById("p1")).thenReturn(Optional.of(product("p1", new Money(new BigDecimal("10.00")), 5)));

        assertThatThrownBy(() -> service.checkout("user-1", List.of(new ItemCheckoutRequest("p1", 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        verify(productRepository, never()).decrementStock(anyString(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_withUnknownProduct_throwsIllegalArgumentException() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout("user-1", List.of(new ItemCheckoutRequest("missing", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
        verify(productRepository, never()).decrementStock(anyString(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_whenStockInsufficient_throwsInsufficientStockExceptionAndDoesNotSave() {
        when(productRepository.findById("p1")).thenReturn(Optional.of(product("p1", new Money(new BigDecimal("10.00")), 0)));
        when(productRepository.decrementStock("p1", 1)).thenReturn(0);

        assertThatThrownBy(() -> service.checkout("user-1", List.of(new ItemCheckoutRequest("p1", 1))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("p1");
        verify(orderRepository, never()).save(any());
    }
}
