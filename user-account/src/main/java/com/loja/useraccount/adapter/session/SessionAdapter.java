package com.loja.useraccount.adapter.session;

import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.out.SessionPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;

/** Session adapter backed by Jakarta EE HttpSession. */
@ApplicationScoped
public class SessionAdapter implements SessionPort {

    @Inject
    HttpServletRequest request;

    @Override
    public void createSession(User user) {
        HttpSession session = request.getSession(true);
        session.setAttribute("user", user);
        session.setMaxInactiveInterval(30 * 60); // 30-minute timeout
    }

    @Override
    public Optional<User> getCurrentUser() {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object user = session.getAttribute("user");
        return user instanceof User currentUser ? Optional.of(currentUser) : Optional.empty();
    }

    @Override
    public void invalidateSession() {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
