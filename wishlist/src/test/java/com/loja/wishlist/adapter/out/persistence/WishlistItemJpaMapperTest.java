package com.loja.wishlist.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.loja.wishlist.domain.model.WishlistItem;

class WishlistItemJpaMapperTest {

    @Test
    void shouldRoundTripWishlistItem() {
        Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
        WishlistItem item = WishlistItem.reconstitute("w-1", "u-1", "p-1", createdAt);

        WishlistItemJpaEntity jpa = WishlistItemJpaMapper.toJpa(item);

        assertThat(jpa.getId()).isEqualTo("w-1");
        assertThat(jpa.getUserId()).isEqualTo("u-1");
        assertThat(jpa.getProductId()).isEqualTo("p-1");
        assertThat(jpa.getCreatedAt()).isEqualTo(createdAt);

        WishlistItem restored = WishlistItemJpaMapper.toDomain(jpa);
        assertThat(restored.getId()).isEqualTo(item.getId());
        assertThat(restored.getUserId()).isEqualTo(item.getUserId());
        assertThat(restored.getProductId()).isEqualTo(item.getProductId());
        assertThat(restored.getCreatedAt()).isEqualTo(item.getCreatedAt());
    }

    @Test
    void toJpa_shouldMapCreateFactoryOutput() {
        WishlistItem item = WishlistItem.create("u-2", "p-2");

        WishlistItemJpaEntity jpa = WishlistItemJpaMapper.toJpa(item);

        assertThat(jpa.getId()).isEqualTo(item.getId());
        assertThat(jpa.getUserId()).isEqualTo("u-2");
        assertThat(jpa.getProductId()).isEqualTo("p-2");
        assertThat(jpa.getCreatedAt()).isEqualTo(item.getCreatedAt());
    }
}
