package com.loja.shared.event;

import java.time.Instant;

/**
 * Base contract for domain events exchanged between modules
 * (e.g., OrderPlacedEvent, UserRegisteredEvent), enabling loose
 * coupling between modules in the monolith.
 */
public interface DomainEvent {
    Instant occurredAt();
}
