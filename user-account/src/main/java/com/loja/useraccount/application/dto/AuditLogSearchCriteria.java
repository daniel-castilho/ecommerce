package com.loja.useraccount.application.dto;

import java.time.Instant;

/**
 * Filters for the admin audit log query (backlog S24 debt). Every field is
 * optional: a {@code null} (or blank) value means "no restriction" for that
 * dimension. Date bounds are inclusive instants.
 *
 * @param actorId         substring match against the acting admin id.
 * @param eventType       exact match on the event type (see the distinct list).
 * @param detailsKeyword  substring match against the free-text details.
 * @param from            earliest created-at instant (inclusive).
 * @param to              latest created-at instant (inclusive).
 */
public record AuditLogSearchCriteria(String actorId,
                                     String eventType,
                                     String detailsKeyword,
                                     Instant from,
                                     Instant to) {

    public static AuditLogSearchCriteria empty() {
        return new AuditLogSearchCriteria(null, null, null, null, null);
    }
}
