package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;

/**
 * Session-scoped JSF bean exposing the authenticated user to templates.
 * Enables guards such as #{userBean.hasRole('ADMIN')} without business logic (SRP).
 */
@Named("userBean")
@SessionScoped
public class UserBean implements Serializable {

    @Inject
    private SessionPort session;

    public User getCurrentUser() {
        return session.getCurrentUser().orElse(null);
    }

    public boolean isLoggedIn() {
        return session.getCurrentUser().isPresent();
    }

    public boolean hasRole(String role) {
        return session.getCurrentUser()
                .map(user -> {
                    try {
                        return user.hasRole(Role.valueOf(role));
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .orElse(false);
    }
}
