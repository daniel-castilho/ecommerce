package com.loja.useraccount.adapter.out.event;

import com.loja.shared.event.DomainEvent;
import com.loja.useraccount.domain.event.AddressAddedEvent;
import com.loja.useraccount.domain.event.AddressRemovedEvent;
import com.loja.useraccount.domain.event.PasswordChangedEvent;
import com.loja.useraccount.domain.event.PasswordResetRequestedEvent;
import com.loja.useraccount.domain.event.RoleAssignedEvent;
import com.loja.useraccount.domain.event.UserLoggedInEvent;
import com.loja.useraccount.domain.event.UserRegisteredEvent;
import com.loja.useraccount.domain.port.out.AuditLogPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuditLogObserver {

    @Inject
    private AuditLogPort auditLog;

    void onUserRegistered(@Observes UserRegisteredEvent event) {
        auditLog.logEvent(event.userId(), null, "REGISTRATION", null, null, "New user registered");
    }

    void onUserLoggedIn(@Observes UserLoggedInEvent event) {
        auditLog.logEvent(event.userId(), null, "LOGIN_SUCCESS", null, null, "Logged in successfully");
    }

    void onPasswordChanged(@Observes PasswordChangedEvent event) {
        auditLog.logEvent(event.userId(), null, "PASSWORD_CHANGE", null, null, "Password changed");
    }

    void onPasswordResetRequested(@Observes PasswordResetRequestedEvent event) {
        auditLog.logEvent(event.userId(), null, "PASSWORD_RESET_REQUESTED", null, null, "Reset token sent");
    }

    void onRoleAssigned(@Observes RoleAssignedEvent event) {
        auditLog.logEvent(event.userId(), event.assignedBy(), "ROLE_ASSIGNED", null, null,
                "Role assigned: " + event.role());
    }

    void onAddressAdded(@Observes AddressAddedEvent event) {
        auditLog.logEvent(event.userId(), null, "ADDRESS_ADDED", null, null, "Address added: " + event.address().getStreet());
    }

    void onAddressRemoved(@Observes AddressRemovedEvent event) {
        auditLog.logEvent(event.userId(), null, "ADDRESS_REMOVED", null, null, "Address removed: " + event.addressId());
    }
}
