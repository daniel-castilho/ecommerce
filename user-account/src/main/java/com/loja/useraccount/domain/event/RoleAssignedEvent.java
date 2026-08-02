package com.loja.useraccount.domain.event;

import com.loja.shared.event.DomainEvent;
import com.loja.useraccount.domain.model.Role;
import java.time.Instant;

public record RoleAssignedEvent(String userId, Role role, String assignedBy, Instant occurredAt) implements DomainEvent {
    public RoleAssignedEvent(String userId, Role role, String assignedBy) {
        this(userId, role, assignedBy, Instant.now());
    }
}
