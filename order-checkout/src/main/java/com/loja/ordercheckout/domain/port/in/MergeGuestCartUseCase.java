package com.loja.ordercheckout.domain.port.in;

/**
 * Folds an anonymous session cart into the authenticated user's cart.
 *
 * <p><b>Given</b> a guest session id and a user id, when the guest has a non-empty
 * cart, then its lines are merged into the user's cart (quantities sum for
 * overlapping products) and the guest cart is removed. No-op when the guest has
 * no cart. Invoked once, right after a successful login, so the shopper never
 * loses what they picked while browsing anonymously.
 */
public interface MergeGuestCartUseCase {

    /**
     * Merge the guest's cart into the user's cart and delete the guest cart.
     *
     * @param guestId anonymous session id the guest cart is keyed by
     * @param userId  authenticated owner that just logged in
     */
    void merge(String guestId, String userId);
}
