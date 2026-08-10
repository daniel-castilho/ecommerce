package com.loja.ordercheckout.adapter.in.web;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.UUID;

/**
 * Identifies the anonymous shopper for the duration of one browser session.
 *
 * <p>Guests browse and add products to a cart keyed by this random id (see
 * {@code CartBean}); the id survives {@code HttpServletRequest#changeSessionId()}
 * on login — that call rotates the session id but keeps the same {@code
 * HttpSession}, so the {@link com.loja.ordercheckout.adapter.out.event.GuestCartMergeObserver}
 * still finds the guest cart. After a successful merge the observer calls
 * {@link #reset()}.
 */
@Named("guestCartSession")
@SessionScoped
public class GuestCartSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private String guestId;

    /** The session's guest id, generated lazily on first access. */
    public String getGuestId() {
        if (guestId == null) {
            guestId = UUID.randomUUID().toString();
        }
        return guestId;
    }

    /** Forget the guest id once its cart has been merged into a user cart. */
    public void reset() {
        guestId = null;
    }
}
