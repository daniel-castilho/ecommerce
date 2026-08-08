package com.loja.wishlist.domain.port.in;

import java.util.List;

import com.loja.wishlist.application.dto.WishlistItemDTO;

/**
 * Authenticated customer lists their personal wishlist (newest first), and
 * can check whether a specific product is already present.
 */
public interface ListMyWishlistUseCase {

    /**
     * List the current user's wishlist items, newest first, enriched with
     * product display snapshots when available.
     *
     * @param userId authenticated owner
     * @return items; never {@code null}
     */
    List<WishlistItemDTO> list(String userId);

    /**
     * @return true iff the product is already on the user's wishlist
     */
    boolean contains(String userId, String productId);
}
