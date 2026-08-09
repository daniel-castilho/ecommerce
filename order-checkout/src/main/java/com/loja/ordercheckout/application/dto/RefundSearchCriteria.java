package com.loja.ordercheckout.application.dto;

import com.loja.ordercheckout.domain.model.RefundStatus;
import java.time.Instant;

/**
 * Filters for the admin refund-request listing. Every field is optional: a
 * {@code null} (or blank) value means "no restriction" for that dimension.
 * Date bounds are inclusive instants.
 *
 * @param status        exact match on the request status.
 * @param customerQuery substring match against the order's customer email or
 *                      shipping recipient name.
 * @param from          earliest request instant (inclusive).
 * @param to            latest request instant (inclusive).
 * @param sort          attribute to order by (defaults to request date).
 * @param ascending     sort direction; defaults to newest first.
 */
public record RefundSearchCriteria(RefundStatus status,
                                   String customerQuery,
                                   Instant from,
                                   Instant to,
                                   RefundSort sort,
                                   boolean ascending) {

    public static RefundSearchCriteria empty() {
        return new RefundSearchCriteria(null, null, null, null, RefundSort.REQUESTED_DATE, false);
    }
}
