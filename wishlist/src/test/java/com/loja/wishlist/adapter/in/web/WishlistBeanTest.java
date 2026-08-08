package com.loja.wishlist.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.in.GetProductDetailUseCase;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import com.loja.wishlist.application.dto.WishlistItemDTO;
import com.loja.wishlist.domain.exception.ProductNotAvailableException;
import com.loja.wishlist.domain.port.in.AddToWishlistUseCase;
import com.loja.wishlist.domain.port.in.ListMyWishlistUseCase;
import com.loja.wishlist.domain.port.in.RemoveFromWishlistUseCase;

import jakarta.faces.context.FacesContext;

class WishlistBeanTest {

    private static final String SLUG = "smartphone";
    private static final String PRODUCT_ID = "p-1";
    private static final String USER_ID = "u-1";

    private AddToWishlistUseCase addToWishlist;
    private RemoveFromWishlistUseCase removeFromWishlist;
    private ListMyWishlistUseCase listMyWishlist;
    private GetProductDetailUseCase getProductDetail;
    private SessionPort session;
    private User currentUser;
    private WishlistBean bean;

    @BeforeEach
    void setUp() {
        addToWishlist = mock(AddToWishlistUseCase.class);
        removeFromWishlist = mock(RemoveFromWishlistUseCase.class);
        listMyWishlist = mock(ListMyWishlistUseCase.class);
        getProductDetail = mock(GetProductDetailUseCase.class);
        session = mock(SessionPort.class);
        currentUser = mock(User.class);
        when(currentUser.getId()).thenReturn(USER_ID);

        bean = new WishlistBean();
        bean.setAddToWishlist(addToWishlist);
        bean.setRemoveFromWishlist(removeFromWishlist);
        bean.setListMyWishlist(listMyWishlist);
        bean.setGetProductDetail(getProductDetail);
        bean.setSession(session);
    }

    // -------------------------------------------------------------- load

    @Test
    void loadForProductSlug_knownSlug_resolvesProductAndFlag() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(getProductDetail.findActiveBySlug(new Slug(SLUG))).thenReturn(Optional.of(product));
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyWishlist.list(USER_ID)).thenReturn(List.of());
        when(listMyWishlist.contains(USER_ID, PRODUCT_ID)).thenReturn(true);

        bean.loadForProductSlug(SLUG);

        assertThat(bean.isProductResolved()).isTrue();
        assertThat(bean.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(bean.isInWishlist()).isTrue();
        assertThat(bean.isLoggedIn()).isTrue();
    }

    @Test
    void loadForProductSlug_unknownSlug_notResolved() {
        when(getProductDetail.findActiveBySlug(new Slug(SLUG))).thenReturn(Optional.empty());
        when(session.getCurrentUser()).thenReturn(Optional.empty());

        bean.loadForProductSlug(SLUG);

        assertThat(bean.isProductResolved()).isFalse();
        assertThat(bean.isInWishlist()).isFalse();
        assertThat(bean.getItems()).isEmpty();
    }

    @Test
    void loadList_loggedIn_loadsItems() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyWishlist.list(USER_ID)).thenReturn(List.of(itemDto("w-1")));

        bean.loadList();

        assertThat(bean.isHasItems()).isTrue();
        assertThat(bean.getItems()).hasSize(1);
        assertThat(bean.getItems().get(0).id()).isEqualTo("w-1");
        assertThat(bean.isProductResolved()).isFalse();
    }

    @Test
    void loadList_guest_hasEmptyItems() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());

        bean.loadList();

        assertThat(bean.isHasItems()).isFalse();
        assertThat(bean.getItems()).isEmpty();
        assertThat(bean.isLoggedIn()).isFalse();
        verify(listMyWishlist, never()).list(anyString());
    }

    // -------------------------------------------------------------- add

    @Test
    void add_loggedIn_delegatesAndMarksInWishlist() {
        prepareProductDetailNotInWishlist();

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.add();

            verify(addToWishlist).add(USER_ID, PRODUCT_ID);
            assertThat(bean.isInWishlist()).isTrue();
        }
    }

    @Test
    void add_notLoggedIn_doesNotDelegate() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.add();

            verify(addToWishlist, never()).add(anyString(), anyString());
        }
    }

    @Test
    void add_productNotAvailable_swallowsAndDoesNotMarkInWishlist() {
        prepareProductDetailNotInWishlist();
        when(addToWishlist.add(USER_ID, PRODUCT_ID))
                .thenThrow(new ProductNotAvailableException(PRODUCT_ID));

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            FacesContext context = mock(FacesContext.class);
            faces.when(FacesContext::getCurrentInstance).thenReturn(context);

            bean.add();

            assertThat(bean.isInWishlist()).isFalse();
            verify(context).addMessage(eq(null), any());
        }
    }

    // -------------------------------------------------------------- remove

    @Test
    void remove_loggedIn_delegatesAndClearsFlag() {
        prepareProductDetailInWishlist();
        assertThat(bean.isInWishlist()).isTrue();

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));
            when(listMyWishlist.list(USER_ID)).thenReturn(List.of());

            bean.remove();

            verify(removeFromWishlist).remove(USER_ID, PRODUCT_ID);
            assertThat(bean.isInWishlist()).isFalse();
        }
    }

    @Test
    void removeProduct_loggedIn_removesTargetRow() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyWishlist.list(USER_ID)).thenReturn(List.of(itemDto("w-1")));
        bean.loadList();

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));
            when(listMyWishlist.list(USER_ID)).thenReturn(List.of());

            bean.removeProduct(PRODUCT_ID);

            verify(removeFromWishlist).remove(USER_ID, PRODUCT_ID);
            assertThat(bean.isHasItems()).isFalse();
        }
    }

    @Test
    void removeProduct_notLoggedIn_doesNotDelegate() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.removeProduct(PRODUCT_ID);

            verify(removeFromWishlist, never()).remove(anyString(), anyString());
        }
    }

    // -------------------------------------------------------------- toggle

    @Test
    void toggle_whenNotInWishlist_adds() {
        prepareProductDetailNotInWishlist();

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.toggle();

            verify(addToWishlist).add(USER_ID, PRODUCT_ID);
            verify(removeFromWishlist, never()).remove(anyString(), anyString());
        }
    }

    @Test
    void toggle_whenInWishlist_removes() {
        prepareProductDetailInWishlist();

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));
            when(listMyWishlist.list(USER_ID)).thenReturn(List.of());

            bean.toggle();

            verify(removeFromWishlist).remove(USER_ID, PRODUCT_ID);
            verify(addToWishlist, never()).add(anyString(), anyString());
        }
    }

    // ------------------------------------------------------- toggleFor (S10)

    @Test
    void loadList_loggedIn_tracksWishlistedProductIds() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyWishlist.list(USER_ID)).thenReturn(List.of(itemDto("w-1")));

        bean.loadList();

        assertThat(bean.inWishlistFor(PRODUCT_ID)).isTrue();
        assertThat(bean.inWishlistFor("other-product")).isFalse();
    }

    @Test
    void loadList_guest_hasEmptyWishlistSet() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());

        bean.loadList();

        assertThat(bean.inWishlistFor(PRODUCT_ID)).isFalse();
    }

    @Test
    void toggleFor_notInWishlist_delegatesAddAndTracks() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyWishlist.list(USER_ID)).thenReturn(List.of());
        bean.loadList();

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));
            when(listMyWishlist.list(USER_ID)).thenReturn(List.of(itemDto("w-1")));

            bean.toggleFor(PRODUCT_ID);

            verify(addToWishlist).add(USER_ID, PRODUCT_ID);
            verify(removeFromWishlist, never()).remove(anyString(), anyString());
            assertThat(bean.inWishlistFor(PRODUCT_ID)).isTrue();
        }
    }

    @Test
    void toggleFor_inWishlist_delegatesRemoveAndClears() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyWishlist.list(USER_ID)).thenReturn(List.of(itemDto("w-1")));
        bean.loadList();
        assertThat(bean.inWishlistFor(PRODUCT_ID)).isTrue();

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));
            when(listMyWishlist.list(USER_ID)).thenReturn(List.of());

            bean.toggleFor(PRODUCT_ID);

            verify(removeFromWishlist).remove(USER_ID, PRODUCT_ID);
            verify(addToWishlist, never()).add(anyString(), anyString());
            assertThat(bean.inWishlistFor(PRODUCT_ID)).isFalse();
        }
    }

    @Test
    void toggleFor_notLoggedIn_doesNotDelegate() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            faces.when(FacesContext::getCurrentInstance).thenReturn(mock(FacesContext.class));

            bean.toggleFor(PRODUCT_ID);

            verify(addToWishlist, never()).add(anyString(), anyString());
            verify(removeFromWishlist, never()).remove(anyString(), anyString());
        }
    }

    @Test
    void toggleFor_productNotAvailable_swallowsAndStaysOut() {
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyWishlist.list(USER_ID)).thenReturn(List.of());
        bean.loadList();
        when(addToWishlist.add(USER_ID, PRODUCT_ID))
                .thenThrow(new ProductNotAvailableException(PRODUCT_ID));

        try (MockedStatic<FacesContext> faces = mockStatic(FacesContext.class)) {
            FacesContext context = mock(FacesContext.class);
            faces.when(FacesContext::getCurrentInstance).thenReturn(context);

            bean.toggleFor(PRODUCT_ID);

            assertThat(bean.inWishlistFor(PRODUCT_ID)).isFalse();
            verify(context).addMessage(eq(null), any());
        }
    }

    // -------------------------------------------------------------- formatting

    @Test
    void formatDate_and_formatPrice_handleNullAndValues() {
        Instant instant = Instant.parse("2026-08-01T15:30:00Z");

        assertThat(bean.formatDate(null)).isEmpty();
        assertThat(bean.formatDate(instant)).isNotBlank();
        assertThat(bean.formatPrice(null)).isEmpty();
        assertThat(bean.formatPrice(new BigDecimal("12.50"))).isEqualTo("12.50");
    }

    private void prepareProductDetailNotInWishlist() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(getProductDetail.findActiveBySlug(new Slug(SLUG))).thenReturn(Optional.of(product));
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyWishlist.list(USER_ID)).thenReturn(List.of());
        when(listMyWishlist.contains(USER_ID, PRODUCT_ID)).thenReturn(false);
        bean.loadForProductSlug(SLUG);
    }

    private void prepareProductDetailInWishlist() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(getProductDetail.findActiveBySlug(new Slug(SLUG))).thenReturn(Optional.of(product));
        when(session.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(listMyWishlist.list(USER_ID)).thenReturn(List.of(itemDto("w-1")));
        when(listMyWishlist.contains(USER_ID, PRODUCT_ID)).thenReturn(true);
        bean.loadForProductSlug(SLUG);
    }

    private static WishlistItemDTO itemDto(String id) {
        return new WishlistItemDTO(
                id,
                PRODUCT_ID,
                "Smartphone",
                SLUG,
                new BigDecimal("999.90"),
                "https://cdn.example/img.webp",
                Instant.parse("2026-08-01T10:00:00Z"));
    }
}
