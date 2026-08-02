package com.loja.useraccount.adapter.session;

import com.loja.useraccount.domain.exception.SessionExpiredException;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/** CDI producer that resolves the current authenticated user for @CurrentUser injection points. */
@ApplicationScoped
public class CurrentUserProducer {

    @Inject
    private SessionPort session;

    @Produces
    @CurrentUser
    public User currentUser() {
        return session.getCurrentUser()
                .orElseThrow(() -> new SessionExpiredException("User not logged in"));
    }
}
