package com.loja.ordercheckout.adapter.out.event;

import com.loja.ordercheckout.adapter.in.web.GuestCartSession;
import com.loja.ordercheckout.domain.port.in.MergeGuestCartUseCase;
import com.loja.useraccount.domain.event.UserLoggedInEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

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
 */
@ApplicationScoped
public class GuestCartMergeObserver {

    private final MergeGuestCartUseCase mergeGuestCart;
    private final GuestCartSession guestCartSession;

    @Inject
    public GuestCartMergeObserver(MergeGuestCartUseCase mergeGuestCart,
                                  GuestCartSession guestCartSession) {
        this.mergeGuestCart = mergeGuestCart;
        this.guestCartSession = guestCartSession;
    }

    void onUserLoggedIn(@Observes UserLoggedInEvent event) {
        mergeGuestCart.merge(guestCartSession.getGuestId(), event.userId());
        guestCartSession.reset();
    }
}
