package com.loja.ordercheckout.domain.port.in;

/**
 * Authenticated customer empties their cart. Used after a successful order and
 * by the "clear cart" action. Idempotent: no cart means nothing to clear.
 */
public interface ClearCartUseCase {

    /**
     * Remove the user's entire cart (no-op when absent).
     *
     * @param userId authenticated owner (from the session, never from the form alone)
     */
    void clear(String userId);
}
