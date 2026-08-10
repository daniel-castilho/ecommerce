package com.loja.ordercheckout.domain.port.in;

/**
 * Authenticated customer adds a product to their durable cart.
 *
 * <p><b>Given</b> an authenticated user id, a product id and a positive quantity,
 * when the product exists and is ACTIVE, then the product is added to the user's
 * cart (one line per product; adding again increments the quantity).
 *
 * <p>Throws {@link com.loja.ordercheckout.domain.exception.CartProductNotAvailableException}
 * if the product does not exist or is not ACTIVE.
 */
public interface AddToCartUseCase {

    /**
     * Add {@code quantity} of the given product to the user's cart.
     *
     * @param userId    authenticated owner (from the session, never from the form alone)
     * @param productId target product
     * @param quantity  {@code >= 1}
     */
    void add(String userId, String productId, int quantity);
}
