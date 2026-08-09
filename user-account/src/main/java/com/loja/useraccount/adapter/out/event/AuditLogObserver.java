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
        auditLog.logEvent(event.userId(), null, "REGISTRATION", "USER", event.userId(),
                null, null, "New user registered");
    }

    void onUserLoggedIn(@Observes UserLoggedInEvent event) {
        auditLog.logEvent(event.userId(), null, "LOGIN_SUCCESS", "USER", event.userId(),
                null, null, "Logged in successfully");
    }

    void onPasswordChanged(@Observes PasswordChangedEvent event) {
        auditLog.logEvent(event.userId(), null, "PASSWORD_CHANGE", "USER", event.userId(),
                null, null, "Password changed");
    }

    void onPasswordResetRequested(@Observes PasswordResetRequestedEvent event) {
        auditLog.logEvent(event.userId(), null, "PASSWORD_RESET_REQUESTED", "USER", event.userId(),
                null, null, "Reset token sent");
    }

    void onRoleAssigned(@Observes RoleAssignedEvent event) {
        auditLog.logEvent(event.userId(), event.assignedBy(), "ROLE_ASSIGNED", "USER", event.userId(),
                null, null, "Role assigned: " + event.role());
    }

    void onUserBlocked(@Observes UserBlockedEvent event) {
        String actorId = session.getCurrentUser().map(u -> u.getId()).orElse(null);
        auditLog.logEvent(event.userId(), actorId, "USER_BLOCKED", "USER", event.userId(),
                null, null, "User account blocked");
    }

    void onUserUnblocked(@Observes UserUnblockedEvent event) {
        String actorId = session.getCurrentUser().map(u -> u.getId()).orElse(null);
        auditLog.logEvent(event.userId(), actorId, "USER_UNBLOCKED", "USER", event.userId(),
                null, null, "User account unblocked");
    }

    void onAddressAdded(@Observes AddressAddedEvent event) {
        String addressId = event.address().getId() != null ? event.address().getId().toString() : null;
        auditLog.logEvent(event.userId(), null, "ADDRESS_ADDED", "ADDRESS", addressId,
                null, null, "Address added: " + event.address().getStreet());
    }

    void onAddressRemoved(@Observes AddressRemovedEvent event) {
        auditLog.logEvent(event.userId(), null, "ADDRESS_REMOVED", "ADDRESS", event.addressId().toString(),
                null, null, "Address removed: " + event.addressId());
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
        auditLog.logEvent(actorId, actorId, "PRODUCT_ARCHIVED", "PRODUCT", event.productId(), ip, userAgent, details);
    }

    void onRefundProcessed(@Observes RefundProcessedEvent event) {
        String actorId = session.getCurrentUser().map(u -> u.getId()).orElse(null);
        auditLog.logEvent(actorId, actorId, "REFUND_PROCESSED", "REFUND", event.refundId(),
                null, null, "Refund processed: id=" + event.refundId() + ", order=" + event.orderId());
    }

    void onRefundRejected(@Observes RefundRejectedEvent event) {
        String actorId = session.getCurrentUser().map(u -> u.getId()).orElse(null);
        auditLog.logEvent(actorId, actorId, "REFUND_REJECTED", "REFUND", event.refundId(),
                null, null, "Refund rejected: id=" + event.refundId() + ", order=" + event.orderId()
                        + ", reason=" + event.reason());
    }
}
