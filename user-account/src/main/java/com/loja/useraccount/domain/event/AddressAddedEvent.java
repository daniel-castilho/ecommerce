package com.loja.useraccount.domain.event;

import com.loja.shared.event.DomainEvent;
import com.loja.useraccount.domain.model.Address;
import java.time.Instant;

public record AddressAddedEvent(String userId, Address address, Instant occurredAt) implements DomainEvent {
    public AddressAddedEvent(String userId, Address address) {
        this(userId, address, Instant.now());
    }
}
