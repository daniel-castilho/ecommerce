package com.loja.useraccount.domain.event;

import com.loja.shared.event.DomainEvent;
import java.time.Instant;

public record PasswordChangedEvent(String userId, Instant occurredAt) implements DomainEvent {
    public PasswordChangedEvent(String userId) {
        this(userId, Instant.now());
    }
}
