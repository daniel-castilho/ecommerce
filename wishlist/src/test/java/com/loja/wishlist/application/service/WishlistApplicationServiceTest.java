package com.loja.wishlist.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.loja.wishlist.application.dto.ProductSnapshot;
import com.loja.wishlist.application.dto.WishlistItemDTO;
import com.loja.wishlist.domain.exception.DuplicateWishlistItemException;
import com.loja.wishlist.domain.exception.ProductNotAvailableException;
import com.loja.wishlist.domain.model.WishlistItem;
import com.loja.wishlist.domain.port.out.ProductLookupPort;
import com.loja.wishlist.domain.port.out.WishlistRepositoryPort;

class WishlistApplicationServiceTest {

    private static final String USER_ID = "u-1";
    private static final String PRODUCT_ID = "p-1";

    private final WishlistRepositoryPort wishlistRepository = mock(WishlistRepositoryPort.class);
    private final ProductLookupPort productLookup = mock(ProductLookupPort.class);

    private WishlistApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WishlistApplicationService(wishlistRepository, productLookup);
        when(wishlistRepository.save(any(WishlistItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------- add

    @Test
    void add_withActiveProductAndNoPriorItem_persistsAndReturnsId() {
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(snapshot()));
        when(wishlistRepository.findByUserAndProduct(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        String id = service.add(USER_ID, PRODUCT_ID);

        assertThat(id).isNotBlank();
        ArgumentCaptor<WishlistItem> captor = ArgumentCaptor.forClass(WishlistItem.class);
        verify(wishlistRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(captor.getValue().getId()).isEqualTo(id);
    }

    @Test
    void add_whenProductAlreadyOnWishlist_isIdempotentAndDoesNotSave() {
        WishlistItem existing = WishlistItem.reconstitute(
                "w-existing", USER_ID, PRODUCT_ID, Instant.parse("2026-08-01T10:00:00Z"));
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(snapshot()));
        when(wishlistRepository.findByUserAndProduct(USER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(existing));

        String id = service.add(USER_ID, PRODUCT_ID);

        assertThat(id).isEqualTo("w-existing");
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void add_whenProductNotActive_throwsProductNotAvailable() {
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(USER_ID, PRODUCT_ID))
                .isInstanceOf(ProductNotAvailableException.class)
                .hasMessageContaining(PRODUCT_ID);
        verify(wishlistRepository, never()).save(any());
        verify(wishlistRepository, never()).findByUserAndProduct(any(), any());
    }

    @Test
    void add_whenConcurrentDuplicate_returnsExistingId() {
        WishlistItem raced = WishlistItem.reconstitute(
                "w-raced", USER_ID, PRODUCT_ID, Instant.parse("2026-08-01T11:00:00Z"));
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(snapshot()));
        when(wishlistRepository.findByUserAndProduct(USER_ID, PRODUCT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raced));
        when(wishlistRepository.save(any(WishlistItem.class)))
                .thenThrow(new DuplicateWishlistItemException(USER_ID, PRODUCT_ID));

        String id = service.add(USER_ID, PRODUCT_ID);

        assertThat(id).isEqualTo("w-raced");
    }

    @Test
    void add_whenConcurrentDuplicateAndStillMissing_rethrows() {
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(snapshot()));
        when(wishlistRepository.findByUserAndProduct(USER_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(wishlistRepository.save(any(WishlistItem.class)))
                .thenThrow(new DuplicateWishlistItemException(USER_ID, PRODUCT_ID));

        assertThatThrownBy(() -> service.add(USER_ID, PRODUCT_ID))
                .isInstanceOf(DuplicateWishlistItemException.class);
    }

    @Test
    void add_withBlankUserId_throws() {
        assertThatThrownBy(() -> service.add("  ", PRODUCT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void add_withNullUserId_throws() {
        assertThatThrownBy(() -> service.add(null, PRODUCT_ID))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void add_withBlankProductId_throws() {
        assertThatThrownBy(() -> service.add(USER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    // -------------------------------------------------------------- remove

    @Test
    void remove_delegatesToRepository() {
        service.remove(USER_ID, PRODUCT_ID);

        verify(wishlistRepository).deleteByUserAndProduct(USER_ID, PRODUCT_ID);
    }

    @Test
    void remove_withBlankUserId_throws() {
        assertThatThrownBy(() -> service.remove(null, PRODUCT_ID))
                .isInstanceOf(NullPointerException.class);
        verify(wishlistRepository, never()).deleteByUserAndProduct(any(), any());
    }

    @Test
    void remove_withBlankProductId_throws() {
        assertThatThrownBy(() -> service.remove(USER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    // -------------------------------------------------------------- list

    @Test
    void list_returnsMappedDtosWithProductSnapshot() {
        Instant createdAt = Instant.parse("2026-08-01T12:00:00Z");
        WishlistItem item = WishlistItem.reconstitute("w-1", USER_ID, PRODUCT_ID, createdAt);
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(item));
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.of(snapshot()));

        List<WishlistItemDTO> dtos = service.list(USER_ID);

        assertThat(dtos).hasSize(1);
        WishlistItemDTO dto = dtos.get(0);
        assertThat(dto.id()).isEqualTo("w-1");
        assertThat(dto.productId()).isEqualTo(PRODUCT_ID);
        assertThat(dto.productName()).isEqualTo("Smartphone");
        assertThat(dto.productSlug()).isEqualTo("smartphone");
        assertThat(dto.price()).isEqualByComparingTo("999.90");
        assertThat(dto.imageUrl()).isEqualTo("https://cdn.example/img.webp");
        assertThat(dto.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void list_whenProductNoLongerActive_usesFallbackLabels() {
        WishlistItem item = WishlistItem.reconstitute(
                "w-1", USER_ID, PRODUCT_ID, Instant.parse("2026-08-01T12:00:00Z"));
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(item));
        when(productLookup.findActiveById(PRODUCT_ID)).thenReturn(Optional.empty());

        List<WishlistItemDTO> dtos = service.list(USER_ID);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).productName()).isEqualTo("Unavailable product");
        assertThat(dtos.get(0).productSlug()).isNull();
        assertThat(dtos.get(0).price()).isNull();
        assertThat(dtos.get(0).imageUrl()).isNull();
    }

    @Test
    void list_withEmptyWishlist_returnsEmptyList() {
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        assertThat(service.list(USER_ID)).isEmpty();
    }

    @Test
    void list_withBlankUserId_throws() {
        assertThatThrownBy(() -> service.list("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    // -------------------------------------------------------------- contains

    @Test
    void contains_delegatesToRepository() {
        when(wishlistRepository.exists(USER_ID, PRODUCT_ID)).thenReturn(true);

        assertThat(service.contains(USER_ID, PRODUCT_ID)).isTrue();
        verify(wishlistRepository).exists(eq(USER_ID), eq(PRODUCT_ID));
    }

    @Test
    void contains_whenAbsent_returnsFalse() {
        when(wishlistRepository.exists(USER_ID, PRODUCT_ID)).thenReturn(false);

        assertThat(service.contains(USER_ID, PRODUCT_ID)).isFalse();
    }

    @Test
    void contains_withBlankProductId_throws() {
        assertThatThrownBy(() -> service.contains(USER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    private static ProductSnapshot snapshot() {
        return new ProductSnapshot(
                PRODUCT_ID,
                "Smartphone",
                "smartphone",
                new BigDecimal("999.90"),
                "https://cdn.example/img.webp");
    }
}
