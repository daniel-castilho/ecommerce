package com.loja.wishlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WishlistItemTest {

    private static final String USER_ID = "u-1";
    private static final String PRODUCT_ID = "p-1";

    @Test
    void create_shouldGenerateIdAndTimestamp() {
        WishlistItem item = WishlistItem.create(USER_ID, PRODUCT_ID);

        assertThat(item.getId()).isNotBlank();
        assertThat(UUID.fromString(item.getId())).isNotNull();
        assertThat(item.getUserId()).isEqualTo(USER_ID);
        assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void create_shouldTrimSurroundingWhitespace() {
        WishlistItem item = WishlistItem.create("  u-1  ", "  p-1  ");

        assertThat(item.getUserId()).isEqualTo("u-1");
        assertThat(item.getProductId()).isEqualTo("p-1");
    }

    @Test
    void create_shouldRejectBlankUserId() {
        assertThatThrownBy(() -> WishlistItem.create("  ", PRODUCT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void create_shouldRejectNullUserId() {
        assertThatThrownBy(() -> WishlistItem.create(null, PRODUCT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void create_shouldRejectBlankProductId() {
        assertThatThrownBy(() -> WishlistItem.create(USER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void create_shouldRejectNullProductId() {
        assertThatThrownBy(() -> WishlistItem.create(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void reconstitute_shouldRestoreAllFields() {
        Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");

        WishlistItem item = WishlistItem.reconstitute("w-1", USER_ID, PRODUCT_ID, createdAt);

        assertThat(item.getId()).isEqualTo("w-1");
        assertThat(item.getUserId()).isEqualTo(USER_ID);
        assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(item.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void reconstitute_shouldRejectBlankId() {
        assertThatThrownBy(() -> WishlistItem.reconstitute(
                "  ", USER_ID, PRODUCT_ID, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void reconstitute_shouldRejectNullCreatedAt() {
        assertThatThrownBy(() -> WishlistItem.reconstitute("w-1", USER_ID, PRODUCT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void reconstitute_shouldRejectBlankUserId() {
        assertThatThrownBy(() -> WishlistItem.reconstitute(
                "w-1", null, PRODUCT_ID, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void reconstitute_shouldRejectBlankProductId() {
        assertThatThrownBy(() -> WishlistItem.reconstitute(
                "w-1", USER_ID, "  ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void create_shouldProduceDistinctIds() {
        WishlistItem a = WishlistItem.create(USER_ID, PRODUCT_ID);
        WishlistItem b = WishlistItem.create(USER_ID, "p-2");

        assertThat(a.getId()).isNotEqualTo(b.getId());
    }
}
