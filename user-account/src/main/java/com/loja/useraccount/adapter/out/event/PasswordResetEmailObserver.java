package com.loja.useraccount.adapter.out.event;

import com.loja.useraccount.domain.event.PasswordResetRequestedEvent;
import com.loja.useraccount.domain.port.out.NotificationPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class PasswordResetEmailObserver {

    private static final Logger LOG = Logger.getLogger(PasswordResetEmailObserver.class.getName());

    @Inject
    private NotificationPort notification;

    void onPasswordResetRequested(@Observes PasswordResetRequestedEvent event) {
        try {
            notification.sendPasswordResetEmail(event.email(), event.token());
        } catch (Exception e) {
            LOG.warning("Password reset email failed for " + event.email() + ": " + e.getMessage());
        }
    }
}
