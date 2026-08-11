package com.loja.ordercheckout.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record of one notification delivery for a business event. Pure domain: no
 * framework imports. The idempotency key (e.g. {@code ORDER_CONFIRMED:{orderId}})
 * uniquely identifies the event; a row with a given key exists at most once.
 *
 * <p>Since Phase C, the row doubles as a transactional outbox entry: the rendered
 * email payload ({@code recipientEmail}, {@code subject}, {@code body}) is snapshotted
 * at claim time inside the business transaction, and a scheduled task dispatches it
 * asynchronously.
 */
public final class NotificationDelivery {

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
    private final Instant createdAt;
    private Instant updatedAt;

    private NotificationDelivery(String id, String eventType, String aggregateId,
                                 NotificationChannel channel, String idempotencyKey,
                                 NotificationDeliveryStatus status, int attemptCount,
                                 String errorMessage, String recipientEmail, String subject,
                                 String body, Instant createdAt, Instant updatedAt) {
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Creates a new delivery claim (status PENDING, one attempt) with the email snapshot. */
    public static NotificationDelivery create(String idempotencyKey, String eventType,
                                              String aggregateId, NotificationChannel channel,
                                              String recipientEmail, String subject, String body) {
        Instant now = Instant.now();
        return new NotificationDelivery(UUID.randomUUID().toString(), eventType, aggregateId,
                channel, idempotencyKey, NotificationDeliveryStatus.PENDING, 1, null,
                recipientEmail, subject, body, now, now);
    }

    /** Restores an exact persisted snapshot. */
    public static NotificationDelivery reconstitute(String id, String eventType, String aggregateId,
                                                    NotificationChannel channel, String idempotencyKey,
                                                    NotificationDeliveryStatus status, int attemptCount,
                                                    String errorMessage, String recipientEmail,
                                                    String subject, String body, Instant createdAt,
                                                    Instant updatedAt) {
        return new NotificationDelivery(id, eventType, aggregateId, channel, idempotencyKey,
                status, attemptCount, errorMessage, recipientEmail, subject, body, createdAt, updatedAt);
    }

    public void markSent() {
        this.status = NotificationDeliveryStatus.SENT;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = NotificationDeliveryStatus.FAILED;
        this.attemptCount++;
        this.errorMessage = error;
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

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}