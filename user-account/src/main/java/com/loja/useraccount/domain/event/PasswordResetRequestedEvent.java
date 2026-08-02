package com.loja.useraccount.domain.event;

import com.loja.shared.event.DomainEvent;
import java.time.Instant;

public record PasswordResetRequestedEvent(String userId, String email, String token, Instant occurredAt) implements DomainEvent {
    public PasswordResetRequestedEvent(String userId, String email, String token) {
        this(userId, email, token, Instant.now());
    }
}
