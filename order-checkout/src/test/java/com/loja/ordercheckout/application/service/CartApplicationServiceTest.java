package com.loja.ordercheckout.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.loja.ordercheckout.application.dto.CartLineView;
import com.loja.ordercheckout.application.dto.CartView;
import com.loja.ordercheckout.application.dto.ProductSnapshot;
import com.loja.ordercheckout.domain.exception.CartLineNotFoundException;
import com.loja.ordercheckout.domain.exception.CartProductNotAvailableException;
import com.loja.ordercheckout.domain.model.Cart;
import com.loja.ordercheckout.domain.model.CartLine;
import com.loja.ordercheckout.domain.port.out.CartRepositoryPort;
import com.loja.ordercheckout.domain.port.out.ProductLookupPort;
import com.loja.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CartApplicationServiceTest {

    private static final String USER_ID = "u-1";
    private static final String PRODUCT_ID = "p-1";
    private static final String SECOND_PRODUCT_ID = "p-2";

    private final CartRepositoryPort cartRepository = mock(CartRepositoryPort.class);
    private final ProductLookupPort productLookup = mock(ProductLookupPort.class);

    private CartApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CartApplicationService(cartRepository, productLookup);
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------- add

    @Test
    void add_noExistingCart_createsAndSavesCart() {
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(snapshot()));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        service.add(USER_ID, PRODUCT_ID, 1);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(captor.capture());
        Cart saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getLines()).hasSize(1);
        assertThat(saved.getLines().get(0).productId()).isEqualTo(PRODUCT_ID);
        assertThat(saved.getLines().get(0).quantity()).isEqualTo(1);
    }

    @Test
    void add_existingCartWithSameProduct_incrementsQuantity() {
        Cart cart = Cart.reconstitute("cart-1", USER_ID, List.of(new CartLine(PRODUCT_ID, 2)),
                1L, Instant.parse("2026-08-08T10:00:00Z"));
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(snapshot()));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        service.add(USER_ID, PRODUCT_ID, 3);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getLines()).hasSize(1);
        assertThat(captor.getValue().getLines().get(0).quantity()).isEqualTo(5);
        assertThat(captor.getValue().getVersion()).isEqualTo(1L);
    }

    @Test
    void add_productNotActive_throwsWithoutPersisting() {
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(USER_ID, PRODUCT_ID, 1))
                .isInstanceOf(CartProductNotAvailableException.class)
                .hasMessageContaining(PRODUCT_ID);
        verifyNoInteractions(cartRepository);
    }

    @Test
    void add_blankUserId_throws() {
        assertThatThrownBy(() -> service.add("  ", PRODUCT_ID, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        verifyNoInteractions(productLookup);
        verifyNoInteractions(cartRepository);
    }

    @Test
    void add_blankProductId_throws() {
        assertThatThrownBy(() -> service.add(USER_ID, "  ", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
        verifyNoInteractions(cartRepository);
    }

    @Test
    void add_quantityLessThanOne_throws() {
        assertThatThrownBy(() -> service.add(USER_ID, PRODUCT_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity");
        verifyNoInteractions(cartRepository);
    }

    // -------------------------------------------------------------- updateQuantity

    @Test
    void updateQuantity_existingLine_setsExactQuantityAndSaves() {
        Cart cart = Cart.reconstitute("cart-1", USER_ID, List.of(new CartLine(PRODUCT_ID, 1)),
                2L, Instant.parse("2026-08-08T10:00:00Z"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        service.updateQuantity(USER_ID, PRODUCT_ID, 7);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getLines().get(0).quantity()).isEqualTo(7);
        verify(cartRepository, never()).deleteByUserId(USER_ID);
    }

    @Test
    void updateQuantity_zero_deletesCartWhenLastLine() {
        Cart cart = Cart.reconstitute("cart-1", USER_ID, List.of(new CartLine(PRODUCT_ID, 1)),
                2L, Instant.parse("2026-08-08T10:00:00Z"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        service.updateQuantity(USER_ID, PRODUCT_ID, 0);

        verify(cartRepository).deleteByUserId(USER_ID);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateQuantity_absentLine_throws() {
        Cart cart = Cart.reconstitute("cart-1", USER_ID, List.of(new CartLine(SECOND_PRODUCT_ID, 1)),
                1L, Instant.parse("2026-08-08T10:00:00Z"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.updateQuantity(USER_ID, PRODUCT_ID, 2))
                .isInstanceOf(CartLineNotFoundException.class)
                .hasMessageContaining(PRODUCT_ID);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateQuantity_noCart_throws() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateQuantity(USER_ID, PRODUCT_ID, 2))
                .isInstanceOf(CartLineNotFoundException.class)
                .hasMessageContaining(PRODUCT_ID);
    }

    // -------------------------------------------------------------- remove

    @Test
    void remove_existingLine_savesCartWithoutIt() {
        Cart cart = Cart.reconstitute("cart-1", USER_ID,
                List.of(new CartLine(PRODUCT_ID, 1), new CartLine(SECOND_PRODUCT_ID, 2)),
                1L, Instant.parse("2026-08-08T10:00:00Z"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        service.remove(USER_ID, PRODUCT_ID);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getLines())
                .extracting(CartLine::productId).containsExactly(SECOND_PRODUCT_ID);
    }

    @Test
    void remove_lastLine_deletesCart() {
        Cart cart = Cart.reconstitute("cart-1", USER_ID, List.of(new CartLine(PRODUCT_ID, 1)),
                1L, Instant.parse("2026-08-08T10:00:00Z"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        service.remove(USER_ID, PRODUCT_ID);

        verify(cartRepository).deleteByUserId(USER_ID);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void remove_noCart_isIdempotentNoOp() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        service.remove(USER_ID, PRODUCT_ID);

        verify(cartRepository, never()).save(any());
        verify(cartRepository, never()).deleteByUserId(any());
    }

    // -------------------------------------------------------------- clear

    @Test
    void clear_deletesCartByUser() {
        service.clear(USER_ID);

        verify(cartRepository).deleteByUserId(USER_ID);
    }

    @Test
    void clear_blankUserId_throws() {
        assertThatThrownBy(() -> service.clear("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        verifyNoInteractions(cartRepository);
    }

    // -------------------------------------------------------------- getCart

    @Test
    void getCart_returnsLinesEnrichedWithLiveCatalogDataAndSubtotal() {
        Cart cart = Cart.reconstitute("cart-1", USER_ID,
                List.of(new CartLine(PRODUCT_ID, 2), new CartLine(SECOND_PRODUCT_ID, 1)),
                1L, Instant.parse("2026-08-08T10:00:00Z"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productLookup.findActiveById(PRODUCT_ID))
                .thenReturn(Optional.of(snapshot(PRODUCT_ID, "Smartphone", "19.99")));
        when(productLookup.findActiveById(SECOND_PRODUCT_ID))
                .thenReturn(Optional.of(snapshot(SECOND_PRODUCT_ID, "Case", "9.90")));

        CartView view = service.getCart(USER_ID);

        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.lines()).hasSize(2);
        CartLineView first = view.lines().get(0);
        assertThat(first.productId()).isEqualTo(PRODUCT_ID);
        assertThat(first.name()).isEqualTo("Smartphone");
        assertThat(first.slug()).isEqualTo("smartphone");
        assertThat(first.quantity()).isEqualTo(2);
        assertThat(first.unitPrice().getAmount()).isEqualByComparingTo("19.99");
        assertThat(first.lineTotal().getAmount()).isEqualByComparingTo("39.98");
        assertThat(first.imageUrl()).isEqualTo("https://cdn.example/img.webp");
        assertThat(first.available()).isTrue();
        assertThat(view.subtotal().getAmount()).isEqualByComparingTo("49.88");
    }

    @Test
    void getCart_unavailableLine_shownAsUnavailableWithZeroSubtotalContribution() {
        Cart cart = Cart.reconstitute("cart-1", USER_ID,
                List.of(new CartLine(PRODUCT_ID, 2), new CartLine(SECOND_PRODUCT_ID, 1)),
                1L, Instant.parse("2026-08-08T10:00:00Z"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.empty());
        when(productLookup.findActiveById(SECOND_PRODUCT_ID))
                .thenReturn(Optional.of(snapshot(SECOND_PRODUCT_ID, "Case", "9.90")));

        CartView view = service.getCart(USER_ID);

        CartLineView unavailable = view.lines().get(0);
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.name()).isEqualTo("Unavailable product");
        assertThat(unavailable.slug()).isNull();
        assertThat(unavailable.unitPrice().getAmount()).isEqualByComparingTo("0");
        assertThat(unavailable.imageUrl()).isNull();
        assertThat(view.subtotal().getAmount()).isEqualByComparingTo("9.90");
    }

    @Test
    void getCart_allLinesUnavailable_subtotalIsZero() {
        Cart cart = Cart.reconstitute("cart-1", USER_ID, List.of(new CartLine(PRODUCT_ID, 1)),
                1L, Instant.parse("2026-08-08T10:00:00Z"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.empty());

        CartView view = service.getCart(USER_ID);

        assertThat(view.lines()).hasSize(1);
        assertThat(view.lines().get(0).available()).isFalse();
        assertThat(view.subtotal().getAmount()).isEqualByComparingTo("0");
    }

    @Test
    void getCart_noCart_returnsEmptyView() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        CartView view = service.getCart(USER_ID);

        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.lines()).isEmpty();
        assertThat(view.subtotal()).isEqualTo(Money.zero());
    }

    @Test
    void getCart_emptyCart_returnsEmptyView() {
        when(cartRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(Cart.reconstitute("cart-1", USER_ID, List.of(),
                        1L, Instant.parse("2026-08-08T10:00:00Z"))));

        CartView view = service.getCart(USER_ID);

        assertThat(view.lines()).isEmpty();
        assertThat(view.subtotal()).isEqualTo(Money.zero());
        verifyNoInteractions(productLookup);
    }

    @Test
    void getCart_blankUserId_throws() {
        assertThatThrownBy(() -> service.getCart("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        verifyNoInteractions(cartRepository);
    }

    private static ProductSnapshot snapshot() {
        return snapshot(PRODUCT_ID, "Smartphone", "19.99");
    }

    private static ProductSnapshot snapshot(String productId, String name, String price) {
        return new ProductSnapshot(
                productId, name, "smartphone", new Money(new BigDecimal(price)),
                "https://cdn.example/img.webp");
    }
}
