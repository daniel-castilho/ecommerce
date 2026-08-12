package com.loja.ordercheckout.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loja.ordercheckout.application.dto.CartLineView;
import com.loja.ordercheckout.application.dto.CartView;
import com.loja.ordercheckout.domain.exception.CartConcurrentModificationException;
import com.loja.ordercheckout.domain.exception.CartProductNotAvailableException;
import com.loja.ordercheckout.domain.port.in.AddToCartUseCase;
import com.loja.ordercheckout.domain.port.in.ClearCartUseCase;
import com.loja.ordercheckout.domain.port.in.GetCartUseCase;
import com.loja.ordercheckout.domain.port.in.RemoveFromCartUseCase;
import com.loja.ordercheckout.domain.port.in.UpdateCartLineUseCase;
import com.loja.shared.domain.Money;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import jakarta.faces.context.FacesContext;

class CartBeanTest {

    private static final String USER_ID = "u-1";
    private static final String GUEST_ID = "guest-1";
    private static final String PRODUCT_ID = "p-1";
    private static final String SECOND_PRODUCT_ID = "p-2";

    private AddToCartUseCase addToCart;
    private UpdateCartLineUseCase updateCartLine;
    private RemoveFromCartUseCase removeFromCart;
    private GetCartUseCase getCart;
    private ClearCartUseCase clearCart;
    private SessionPort session;
    private GuestCartSession guestCartSession;
    private User currentUser;
    private CartBean bean;

    @BeforeEach
    void setUp() {
        addToCart = mock(AddToCartUseCase.class);
        updateCartLine = mock(UpdateCartLineUseCase.class);
        removeFromCart = mock(RemoveFromCartUseCase.class);
        getCart = mock(GetCartUseCase.class);
        clearCart = mock(ClearCartUseCase.class);
        session = mock(SessionPort.class);
        guestCartSession = mock(GuestCartSession.class);
        when(guestCartSession.getGuestId()).thenReturn(GUEST_ID);
        currentUser = mock(User.class);
        when(currentUser.getId()).thenReturn(USER_ID);

        bean = new CartBean();
        bean.setAddToCart(addToCart);
        bean.setUpdateCartLine(updateCartLine);
        bean.setRemoveFromCart(removeFromCart);
        bean.setGetCart(getCart);
        bean.setClearCart(clearCart);
        bean.setSession(session);
        bean.setGuestCartSession(guestCartSession);
    }

    // -------------------------------------------------------------- load

    @Test
    void load_loggedIn_populatesLinesAndQuantityMap() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(view(List.of(
                line(PRODUCT_ID, "Smartphone", 2), line(SECOND_PRODUCT_ID, "Case", 1))));

        bean.load();

        assertThat(bean.isLoggedIn()).isTrue();
        assertThat(bean.isHasLines()).isTrue();
        assertThat(bean.getLines()).hasSize(2);
        assertThat(bean.getSubtotal().getAmount()).isEqualByComparingTo("59.97");
        assertThat(bean.getQuantityByProduct()).containsEntry(PRODUCT_ID, 2)
                .containsEntry(SECOND_PRODUCT_ID, 1);
    }

    @Test
    void load_guest_loadsCartKeyedByGuestId() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());
        when(getCart.getCart(GUEST_ID)).thenReturn(emptyView());

        bean.load();

        assertThat(bean.isLoggedIn()).isFalse();
        assertThat(bean.isHasLines()).isFalse();
        assertThat(bean.getLines()).isEmpty();
        assertThat(bean.getSubtotal()).isEqualTo(Money.zero());
        verify(getCart).getCart(GUEST_ID);
    }

    // -------------------------------------------------------------- add

    @Test
    void addProduct_loggedIn_delegatesAndReloads() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.addProduct(PRODUCT_ID);
        }

        verify(addToCart).add(USER_ID, PRODUCT_ID, 1);
        verify(getCart).getCart(USER_ID);
    }

    @Test
    void addProduct_guest_delegatesWithGuestId() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());
        when(getCart.getCart(GUEST_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.addProduct(PRODUCT_ID);
        }

        verify(addToCart).add(GUEST_ID, PRODUCT_ID, 1);
        verify(getCart).getCart(GUEST_ID);
    }

    @Test
    void addProduct_productNotAvailable_showsErrorAndDoesNotReload() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(emptyView());
        org.mockito.Mockito.doThrow(new CartProductNotAvailableException(PRODUCT_ID))
                .when(addToCart).add(USER_ID, PRODUCT_ID, 1);

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.addProduct(PRODUCT_ID);
        }

        verify(getCart, never()).getCart(USER_ID);
    }

    @Test
    void addProduct_blankProductId_isNoOp() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));

        bean.addProduct("  ");

        verify(addToCart, never()).add(anyString(), anyString(), anyInt());
    }

    @Test
    void addProduct_concurrentModification_showsMessageAndReloads() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(emptyView());
        org.mockito.Mockito.doThrow(new CartConcurrentModificationException("cart-1"))
                .when(addToCart).add(USER_ID, PRODUCT_ID, 1);

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.addProduct(PRODUCT_ID);
        }

        verify(getCart).getCart(USER_ID);
    }

    // -------------------------------------------------------------- updateQuantity

    @Test
    void updateQuantity_appliesEditedValue() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            bean.getQuantityByProduct().put(PRODUCT_ID, 5);
            bean.updateQuantity(PRODUCT_ID);
        }

        verify(updateCartLine).updateQuantity(USER_ID, PRODUCT_ID, 5);
    }

    @Test
    void updateQuantity_zero_removesLine() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            bean.getQuantityByProduct().put(PRODUCT_ID, 0);
            bean.updateQuantity(PRODUCT_ID);
        }

        verify(updateCartLine).updateQuantity(USER_ID, PRODUCT_ID, 0);
    }

    @Test
    void updateQuantity_invalidValue_showsErrorAndDoesNotDelegate() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.getQuantityByProduct().put(PRODUCT_ID, -3);
            bean.updateQuantity(PRODUCT_ID);
        }

        verify(updateCartLine, never()).updateQuantity(anyString(), anyString(), anyInt());
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateQuantity_elSubmittedString_coercesAndDelegates() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            ((Map<String, Object>) (Map<?, ?>) bean.getQuantityByProduct()).put(PRODUCT_ID, "3");
            bean.updateQuantity(PRODUCT_ID);
        }

        verify(updateCartLine).updateQuantity(USER_ID, PRODUCT_ID, 3);
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateQuantity_garbageValue_showsErrorAndDoesNotDelegate() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            ((Map<String, Object>) (Map<?, ?>) bean.getQuantityByProduct()).put(PRODUCT_ID, "abc");
            bean.updateQuantity(PRODUCT_ID);
        }

        verify(updateCartLine, never()).updateQuantity(anyString(), anyString(), anyInt());
    }

    @Test
    void updateQuantity_guest_delegatesWithGuestId() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());
        when(getCart.getCart(GUEST_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            bean.getQuantityByProduct().put(PRODUCT_ID, 2);
            bean.updateQuantity(PRODUCT_ID);
        }

        verify(updateCartLine).updateQuantity(GUEST_ID, PRODUCT_ID, 2);
    }

    // -------------------------------------------------------------- remove / clear

    @Test
    void removeLine_delegatesAndReloads() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            bean.removeLine(PRODUCT_ID);
        }

        verify(removeFromCart).remove(USER_ID, PRODUCT_ID);
    }

    @Test
    void removeLine_guest_delegatesWithGuestId() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());
        when(getCart.getCart(GUEST_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            bean.removeLine(PRODUCT_ID);
        }

        verify(removeFromCart).remove(GUEST_ID, PRODUCT_ID);
    }

    @Test
    void clearCart_delegates() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            bean.clearCart();
        }

        verify(clearCart).clear(USER_ID);
    }

    @Test
    void clearCart_guest_delegatesWithGuestId() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());
        when(getCart.getCart(GUEST_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            bean.clearCart();
        }

        verify(clearCart).clear(GUEST_ID);
    }

    // -------------------------------------------------------------- proceedToCheckout

    @Test
    void proceedToCheckout_withLines_returnsCheckoutOutcome() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(view(List.of(line(PRODUCT_ID, "Smartphone", 1))));

        bean.load();

        assertThat(bean.proceedToCheckout()).isEqualTo("/order-checkout/checkout.xhtml?faces-redirect=true");
    }

    @Test
    void proceedToCheckout_emptyCart_staysOnPage() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(getCart.getCart(USER_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            assertThat(bean.proceedToCheckout()).isNull();
        }
    }

    @Test
    void proceedToCheckout_guestWithLines_redirectsToLogin() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());
        when(getCart.getCart(GUEST_ID)).thenReturn(view(List.of(line(PRODUCT_ID, "Smartphone", 1))));

        bean.load();

        assertThat(bean.proceedToCheckout()).isEqualTo("/user-account/login.xhtml?faces-redirect=true");
    }

    @Test
    void proceedToCheckout_guestWithEmptyCart_staysOnPage() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());
        when(getCart.getCart(GUEST_ID)).thenReturn(emptyView());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.load();
            assertThat(bean.proceedToCheckout()).isNull();
        }
    }

    private static CartLineView line(String productId, String name, int quantity) {
        Money unitPrice = new Money(new BigDecimal("19.99"));
        return new CartLineView(productId, name, "slug", quantity, unitPrice,
                unitPrice.multiply(quantity), "https://cdn.example/img.webp", true, Set.of(1L));
    }

    private static CartView view(List<CartLineView> lines) {
        Money subtotal = lines.stream()
                .filter(CartLineView::available)
                .map(CartLineView::lineTotal)
                .reduce(Money.zero(), Money::add);
        return new CartView(USER_ID, lines, subtotal);
    }

    private static CartView emptyView() {
        return new CartView(USER_ID, List.of(), Money.zero());
    }
}
