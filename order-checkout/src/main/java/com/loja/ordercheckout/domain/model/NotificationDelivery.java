package com.loja.ordercheckout.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit record of one notification delivery for a business event. Pure domain: no
 * framework imports. The idempotency key (e.g. {@code ORDER_CONFIRMED:{orderId}})
 * uniquely identifies the event; a row with a given key exists at most once.
 *
 * <p>Since Phase C, the row doubles as a transactional outbox entry: the rendered
 * email payload ({@code recipientEmail}, {@code subject}, {@code body} and, from Phase E,
 * {@code bodyHtml}) is snapshotted at claim time inside the business transaction, and a
 * scheduled task dispatches it asynchronously. {@code bodyHtml} is nullable: rows claimed
 * before Phase E carry only the text body and are sent text-only.
 *
 * <p>Since Phase D the dispatch policy lives here: a row is due while {@code attemptCount <
 * MAX_ATTEMPTS}; a failure schedules the next try with {@link #backoffDelayFor(int)} and the
 * last failure escalates the status to {@link NotificationDeliveryStatus#EXHAUSTED} (never
 * polled again, visible in the admin delivery log for manual resend).
 */
public final class NotificationDelivery {

    /** Total dispatch tries before a delivery is marked EXHAUSTED. */
    public static final int MAX_ATTEMPTS = 3;

    private final String id;
    private final String eventType;
    private final String aggregateId;
    private final NotificationChannel channel;
    private final String idempotencyKey;
    private NotificationDeliveryStatus status;
    private int attemptCount;
    private String errorMessage;
    private String recipientEmail;
    private String subject;
    private String body;
    private String bodyHtml;
    private Instant nextAttemptAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private NotificationDelivery(String id, String eventType, String aggregateId,
                                 NotificationChannel channel, String idempotencyKey,
                                 NotificationDeliveryStatus status, int attemptCount,
                                 String errorMessage, String recipientEmail, String subject,
                                 String body, String bodyHtml, Instant nextAttemptAt,
                                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.channel = channel;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.attemptCount = attemptCount;
        this.errorMessage = errorMessage;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
        this.bodyHtml = bodyHtml;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Delay before the next dispatch attempt after a failure: exponential-ish backoff
     * (30 s after the 1st failure, 2 min after the 2nd, then a cap). The argument is the
     * attempt count AFTER the failure was recorded.
     */
    public static Duration backoffDelayFor(int attemptAfterFailure) {
        return switch (attemptAfterFailure) {
            case 1 -> Duration.ofSeconds(30);
            case 2 -> Duration.ofMinutes(2);
            default -> Duration.ofMinutes(5);
        };
    }

    /**
     * Creates a new delivery claim (status PENDING, zero failed attempts, eligible for the
     * first dispatch immediately) with the email snapshot.
     */
    public static NotificationDelivery create(String idempotencyKey, String eventType,
                                              String aggregateId, NotificationChannel channel,
                                              String recipientEmail, String subject, String body) {
        return create(idempotencyKey, eventType, aggregateId, channel, recipientEmail, subject,
                body, null);
    }

    /**
     * Creates a new delivery claim (status PENDING, zero failed attempts, eligible for the
     * first dispatch immediately) with the email snapshot and its HTML variant.
     */
    public static NotificationDelivery create(String idempotencyKey, String eventType,
                                              String aggregateId, NotificationChannel channel,
                                              String recipientEmail, String subject, String body,
                                              String bodyHtml) {
        Instant now = Instant.now();
        return new NotificationDelivery(UUID.randomUUID().toString(), eventType, aggregateId,
                channel, idempotencyKey, NotificationDeliveryStatus.PENDING, 0, null,
                recipientEmail, subject, body, bodyHtml, now, now, now);
    }

    /** Restores an exact persisted snapshot. */
    public static NotificationDelivery reconstitute(String id, String eventType, String aggregateId,
                                                    NotificationChannel channel, String idempotencyKey,
                                                    NotificationDeliveryStatus status, int attemptCount,
                                                    String errorMessage, String recipientEmail,
                                                    String subject, String body, String bodyHtml,
                                                    Instant nextAttemptAt, Instant createdAt,
                                                    Instant updatedAt) {
        return new NotificationDelivery(id, eventType, aggregateId, channel, idempotencyKey,
                status, attemptCount, errorMessage, recipientEmail, subject, body, bodyHtml,
                nextAttemptAt, createdAt, updatedAt);
    }

    public void markSent() {
        this.status = NotificationDeliveryStatus.SENT;
        this.errorMessage = null;
        this.nextAttemptAt = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = NotificationDeliveryStatus.FAILED;
        this.attemptCount++;
        this.errorMessage = error;
        this.nextAttemptAt = Instant.now().plus(backoffDelayFor(this.attemptCount));
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }

    public String getEventType() { return eventType; }

    public String getAggregateId() { return aggregateId; }

    public NotificationChannel getChannel() { return channel; }

    public String getIdempotencyKey() { return idempotencyKey; }

    public NotificationDeliveryStatus getStatus() { return status; }

    public int getAttemptCount() { return attemptCount; }

    public String getErrorMessage() { return errorMessage; }

    public String getRecipientEmail() { return recipientEmail; }

    public String getSubject() { return subject; }

    public String getBody() { return body; }

    public String getBodyHtml() { return bodyHtml; }

    public Instant getNextAttemptAt() { return nextAttemptAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}