package com.loja.ordercheckout.domain.port.in;

/**
 * Authenticated customer sets the exact quantity of a line on their cart.
 *
 * <p>A quantity of zero removes the line. Targeting a product that is not on the
 * cart throws {@link com.loja.ordercheckout.domain.exception.CartLineNotFoundException}.
 */
public interface UpdateCartLineUseCase {

    /**
     * Set the exact quantity for an existing line of the user's cart.
     *
     * @param userId    authenticated owner (from the session, never from the form alone)
     * @param productId target product
     * @param quantity  {@code >= 0}
     */
    void updateQuantity(String userId, String productId, int quantity);
}
