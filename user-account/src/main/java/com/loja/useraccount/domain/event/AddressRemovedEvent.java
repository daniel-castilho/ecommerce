package com.loja.useraccount.domain.event;

import com.loja.shared.event.DomainEvent;
import java.time.Instant;

public record AddressRemovedEvent(String userId, Long addressId, Instant occurredAt) implements DomainEvent {
    public AddressRemovedEvent(String userId, Long addressId) {
        this(userId, addressId, Instant.now());
    }
}
