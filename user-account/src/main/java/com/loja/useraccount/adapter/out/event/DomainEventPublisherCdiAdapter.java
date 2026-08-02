package com.loja.useraccount.adapter.out.event;

import com.loja.shared.event.DomainEvent;
import com.loja.shared.event.DomainEventPublisherPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class DomainEventPublisherCdiAdapter implements DomainEventPublisherPort {

    @Inject
    private Event<DomainEvent> eventBus;

    @Override
    public void publish(DomainEvent event) {
        eventBus.fire(event);
    }
}
