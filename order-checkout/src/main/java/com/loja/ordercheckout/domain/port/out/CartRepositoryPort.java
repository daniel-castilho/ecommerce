package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.domain.model.Cart;
import java.util.Optional;

/**
 * Persistence port for the {@link Cart} aggregate.
 *
 * <p>MVP keeps a single active cart per user: {@code user_id} is unique at the
 * database level and implementations must surface optimistic-lock conflicts as
 * {@link com.loja.ordercheckout.domain.exception.CartConcurrentModificationException}.
 */
public interface CartRepositoryPort {

    /**
     * @return the user's active cart, empty when they have never created one
     */
    Optional<Cart> findByUserId(String userId);

    /**
     * Insert or update the cart (with its lines), returning the persisted
     * snapshot including the fresh optimistic-lock version.
     *
     * @throws com.loja.ordercheckout.domain.exception.CartConcurrentModificationException
     *         when the version in {@code cart} no longer matches the stored row
     */
    Cart save(Cart cart);

    /**
     * Delete the user's cart and all its lines (idempotent no-op when absent).
     */
    void deleteByUserId(String userId);
}
