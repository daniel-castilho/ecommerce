package com.loja.useraccount.domain.event;

import com.loja.shared.event.DomainEvent;
import java.time.Instant;

public record UserBlockedEvent(String userId, Instant occurredAt) implements DomainEvent {
    public UserBlockedEvent(String userId) {
        this(userId, Instant.now());
    }
}
