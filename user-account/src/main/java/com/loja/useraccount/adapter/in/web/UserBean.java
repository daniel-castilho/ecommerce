package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.SecurityContext;
import java.io.Serializable;

/**
 * Session-scoped JSF bean exposing the authenticated user to templates.
 * Login state and role checks come from the container's {@link SecurityContext}
 * (real Jakarta Security identity); the domain user itself is read from the
 * application session so templates can render profile data.
 */
@Named("userBean")
@SessionScoped
public class UserBean implements Serializable {

    @Inject
    private SessionPort session;

    @Inject
    private SecurityContext securityContext;

    public User getCurrentUser() {
        return session.getCurrentUser().orElse(null);
    }

    public boolean isLoggedIn() {
        return securityContext.getCallerPrincipal() != null;
    }

    public boolean hasRole(String role) {
        return securityContext.isCallerInRole(role);
    }
}
