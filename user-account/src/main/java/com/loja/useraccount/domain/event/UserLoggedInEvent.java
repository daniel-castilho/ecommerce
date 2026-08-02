package com.loja.useraccount.domain.event;

import com.loja.shared.event.DomainEvent;
import java.time.Instant;

public record UserLoggedInEvent(String userId, String email, Instant occurredAt) implements DomainEvent {
    public UserLoggedInEvent(String userId, String email) {
        this(userId, email, Instant.now());
    }
}
