package com.loja.shared.event;

import java.time.Instant;

/** Cross-module domain event: a product was archived. Includes SKU and name for richer audit details. */
public record ProductArchivedEvent(String productId, String sku, String name, Instant occurredAt) implements DomainEvent {
}
