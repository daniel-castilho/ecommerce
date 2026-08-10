package com.loja.ordercheckout.domain.port.in;

/**
 * Authenticated customer removes a product from their cart.
 *
 * <p>Idempotent: removing a product that is not on the cart is a no-op.
 */
public interface RemoveFromCartUseCase {

    /**
     * Remove the line for the given product (no-op when absent).
     *
     * @param userId    authenticated owner (from the session, never from the form alone)
     * @param productId target product
     */
    void remove(String userId, String productId);
}
