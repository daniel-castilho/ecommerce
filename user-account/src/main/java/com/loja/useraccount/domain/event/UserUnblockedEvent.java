package com.loja.useraccount.domain.event;

import com.loja.shared.event.DomainEvent;
import java.time.Instant;

public record UserUnblockedEvent(String userId, Instant occurredAt) implements DomainEvent {
    public UserUnblockedEvent(String userId) {
        this(userId, Instant.now());
    }
}
