package com.loja.ordercheckout.adapter.out.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loja.ordercheckout.adapter.in.web.GuestCartSession;
import com.loja.ordercheckout.domain.port.in.MergeGuestCartUseCase;
import com.loja.useraccount.domain.event.UserLoggedInEvent;
import org.junit.jupiter.api.Test;

class GuestCartMergeObserverTest {

    private static final String GUEST_ID = "guest-1";
    private static final String USER_ID = "user-1";

    private final MergeGuestCartUseCase mergeGuestCart = mock(MergeGuestCartUseCase.class);
    private final GuestCartSession guestCartSession = mock(GuestCartSession.class);

    private final GuestCartMergeObserver observer =
            new GuestCartMergeObserver(mergeGuestCart, guestCartSession);

    @Test
    void onUserLoggedIn_mergesGuestCartIntoUserAndResets() {
        when(guestCartSession.getGuestId()).thenReturn(GUEST_ID);

        observer.onUserLoggedIn(new UserLoggedInEvent(USER_ID, "a@example.com"));

        verify(mergeGuestCart).merge(GUEST_ID, USER_ID);
        verify(guestCartSession).reset();
    }
}
