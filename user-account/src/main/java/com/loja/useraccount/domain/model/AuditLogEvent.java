package com.loja.useraccount.domain.model;

import java.time.Instant;

public record AuditLogEvent(
    Long id,
    String userId,
    String actorId,
    String eventType,
    String entityType,
    String entityId,
    String ipAddress,
    String userAgent,
    String details,
    Instant createdAt
) {}
