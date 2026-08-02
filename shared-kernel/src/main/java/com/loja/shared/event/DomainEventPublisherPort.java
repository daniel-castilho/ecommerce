package com.loja.shared.event;

/**
 * Shared output port: any module can publish events
 * without knowing how (CDI Event, queue, etc.) they are propagated.
 */
public interface DomainEventPublisherPort {
    void publish(DomainEvent event);
}
