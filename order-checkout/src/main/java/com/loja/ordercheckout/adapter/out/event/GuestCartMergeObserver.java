package com.loja.ordercheckout.adapter.out.event;

import com.loja.ordercheckout.adapter.in.web.GuestCartSession;
import com.loja.ordercheckout.domain.exception.CartConcurrentModificationException;
import com.loja.ordercheckout.domain.port.in.MergeGuestCartUseCase;
import com.loja.useraccount.domain.event.UserLoggedInEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Folds the guest cart into the user's cart the moment a login succeeds.
 *
 * <p>Listens for {@link UserLoggedInEvent}, published synchronously by
 * {@code LoginUseCase.establishSession(...)} right after the container
 * authenticated the caller. At that point the {@code HttpSession} is the same
 * object (only its id rotated via {@code changeSessionId()}), so the
 * {@link GuestCartSession} still holds the id of the cart the guest built while
 * anonymous. After the merge the guest id is forgotten, so any later logout →
 * fresh guest gets a brand new empty cart.
 *
 * <p>The merge must never fail a login: an optimistic-lock conflict
 * ({@link CartConcurrentModificationException}) is retried on a fresh
 * transaction, and any other failure is logged and swallowed.
 */
@ApplicationScoped
public class GuestCartMergeObserver {

    private static final Logger LOGGER = Logger.getLogger(GuestCartMergeObserver.class.getName());
    private static final int MAX_ATTEMPTS = 3;

    private final MergeGuestCartUseCase mergeGuestCart;
    private final GuestCartSession guestCartSession;

    @Inject
    public GuestCartMergeObserver(MergeGuestCartUseCase mergeGuestCart,
                                  GuestCartSession guestCartSession) {
        this.mergeGuestCart = mergeGuestCart;
        this.guestCartSession = guestCartSession;
    }

    void onUserLoggedIn(@Observes UserLoggedInEvent event) {
        try {
            mergeWithRetry(guestCartSession.getGuestId(), event.userId());
            guestCartSession.reset();
        } catch (CartConcurrentModificationException e) {
            LOGGER.log(Level.WARNING,
                    "Guest cart merge for user {0} failed after {1} attempts; login continues.",
                    new Object[]{event.userId(), MAX_ATTEMPTS});
            guestCartSession.reset();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Guest cart merge for user {0} failed: {1}",
                    new Object[]{event.userId(), e.getMessage()});
            guestCartSession.reset();
        }
    }

    private void mergeWithRetry(String guestId, String userId) {
        CartConcurrentModificationException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                mergeGuestCart.merge(guestId, userId);
                return;
            } catch (CartConcurrentModificationException e) {
                lastConflict = e;
                LOGGER.log(Level.FINE, "Guest cart merge attempt {0} hit an optimistic-lock conflict; retrying.",
                        attempt);
            }
        }
        throw lastConflict;
    }
}
