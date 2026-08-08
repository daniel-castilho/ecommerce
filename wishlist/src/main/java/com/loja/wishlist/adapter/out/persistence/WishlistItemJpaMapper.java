package com.loja.wishlist.adapter.out.persistence;

import com.loja.wishlist.domain.model.WishlistItem;

/**
 * Sole place where a {@link WishlistItem} domain object is converted to/from
 * {@link WishlistItemJpaEntity}. Nothing outside the persistence adapter may
 * reach into a JPA entity directly.
 */
public final class WishlistItemJpaMapper {

    private WishlistItemJpaMapper() {}

    public static WishlistItemJpaEntity toJpa(WishlistItem item) {
        WishlistItemJpaEntity e = new WishlistItemJpaEntity();
        e.setId(item.getId());
        e.setUserId(item.getUserId());
        e.setProductId(item.getProductId());
        e.setCreatedAt(item.getCreatedAt());
        return e;
    }

    public static WishlistItem toDomain(WishlistItemJpaEntity e) {
        return WishlistItem.reconstitute(
                e.getId(),
                e.getUserId(),
                e.getProductId(),
                e.getCreatedAt());
    }
}
