package com.loja.useraccount.adapter.out.event;

import com.loja.useraccount.domain.event.UserRegisteredEvent;
import com.loja.useraccount.domain.port.out.NotificationPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class WelcomeEmailObserver {

    private static final Logger LOG = Logger.getLogger(WelcomeEmailObserver.class.getName());

    @Inject
    private NotificationPort notification;

    void onUserRegistered(@Observes UserRegisteredEvent event) {
        try {
            notification.sendWelcomeEmail(event.email(), event.fullName());
        } catch (Exception e) {
            LOG.warning("Welcome email failed for " + event.email() + ": " + e.getMessage());
        }
    }
}
