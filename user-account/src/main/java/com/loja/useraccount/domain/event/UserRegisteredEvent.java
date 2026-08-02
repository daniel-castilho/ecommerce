package com.loja.useraccount.domain.event;

import com.loja.shared.event.DomainEvent;
import java.time.Instant;

public record UserRegisteredEvent(String userId, String email, String fullName, Instant occurredAt) implements DomainEvent {
    public UserRegisteredEvent(String userId, String email, String fullName) {
        this(userId, email, fullName, Instant.now());
    }
}
