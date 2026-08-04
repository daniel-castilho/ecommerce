package com.loja.useraccount.adapter.out.event;

import com.loja.shared.event.ProductArchivedEvent;
import com.loja.shared.event.RefundProcessedEvent;
import com.loja.shared.event.RefundRejectedEvent;
import com.loja.useraccount.domain.event.AddressAddedEvent;
import com.loja.useraccount.domain.event.AddressRemovedEvent;
import com.loja.useraccount.domain.event.PasswordChangedEvent;
import com.loja.useraccount.domain.event.PasswordResetRequestedEvent;
import com.loja.useraccount.domain.event.RoleAssignedEvent;
import com.loja.useraccount.domain.event.UserBlockedEvent;
import com.loja.useraccount.domain.event.UserLoggedInEvent;
import com.loja.useraccount.domain.event.UserRegisteredEvent;
import com.loja.useraccount.domain.event.UserUnblockedEvent;
import com.loja.useraccount.domain.port.out.AuditLogPort;
import com.loja.useraccount.domain.port.out.SessionPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuditLogObserver {

    @Inject
    private AuditLogPort auditLog;

    @Inject
    private SessionPort session;

    @Inject
    private jakarta.servlet.http.HttpServletRequest request;

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

    void onUserBlocked(@Observes UserBlockedEvent event) {
        String actorId = session.getCurrentUser().map(u -> u.getId()).orElse(null);
        auditLog.logEvent(event.userId(), actorId, "USER_BLOCKED", null, null,
                "User account blocked");
    }

    void onUserUnblocked(@Observes UserUnblockedEvent event) {
        String actorId = session.getCurrentUser().map(u -> u.getId()).orElse(null);
        auditLog.logEvent(event.userId(), actorId, "USER_UNBLOCKED", null, null,
                "User account unblocked");
    }

    void onAddressAdded(@Observes AddressAddedEvent event) {
        auditLog.logEvent(event.userId(), null, "ADDRESS_ADDED", null, null, "Address added: " + event.address().getStreet());
    }

    void onAddressRemoved(@Observes AddressRemovedEvent event) {
        auditLog.logEvent(event.userId(), null, "ADDRESS_REMOVED", null, null, "Address removed: " + event.addressId());
    }

    void onProductArchived(@Observes ProductArchivedEvent event) {
        String actorId = session.getCurrentUser().map(u -> u.getId()).orElse(null);
        String ip = null;
        try {
            ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) {
                ip = request.getRemoteAddr();
            }
        } catch (Exception ignore) {
            ip = null;
        }
        String userAgent = null;
        try {
            userAgent = request.getHeader("User-Agent");
        } catch (Exception ignore) {
            userAgent = null;
        }
        String details = String.format("Product archived: id=%s, sku=%s, name=%s", event.productId(), event.sku(), event.name());
        auditLog.logEvent(actorId, null, "PRODUCT_ARCHIVED", ip, userAgent, details);
    }

    void onRefundProcessed(@Observes RefundProcessedEvent event) {
        String actorId = session.getCurrentUser().map(u -> u.getId()).orElse(null);
        auditLog.logEvent(actorId, null, "REFUND_PROCESSED", null, null,
                "Refund processed: id=" + event.refundId() + ", order=" + event.orderId());
    }

    void onRefundRejected(@Observes RefundRejectedEvent event) {
        String actorId = session.getCurrentUser().map(u -> u.getId()).orElse(null);
        auditLog.logEvent(actorId, null, "REFUND_REJECTED", null, null,
                "Refund rejected: id=" + event.refundId() + ", order=" + event.orderId()
                        + ", reason=" + event.reason());
    }
}
