package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.NotificationChannel;
import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "tb_notification_delivery_log",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_delivery_idempotency",
                columnNames = "idempotency_key"))
public class NotificationDeliveryLogJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(length = 255)
    private String subject;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationDeliveryLogJpaEntity() {
    }

    public NotificationDeliveryLogJpaEntity(NotificationDelivery delivery) {
        this.id = delivery.getId();
        this.eventType = delivery.getEventType();
        this.aggregateId = delivery.getAggregateId();
        this.channel = delivery.getChannel();
        this.status = delivery.getStatus();
        this.attemptCount = delivery.getAttemptCount();
        this.idempotencyKey = delivery.getIdempotencyKey();
        this.errorMessage = delivery.getErrorMessage();
        this.recipientEmail = delivery.getRecipientEmail();
        this.subject = delivery.getSubject();
        this.body = delivery.getBody();
        this.createdAt = delivery.getCreatedAt();
        this.updatedAt = delivery.getUpdatedAt();
    }

    public NotificationDelivery toDomain() {
        return NotificationDelivery.reconstitute(id, eventType, aggregateId, channel,
                idempotencyKey, status, attemptCount, errorMessage, recipientEmail,
                subject, body, createdAt, updatedAt);
    }

    public String getId() { return id; }

    public String getEventType() { return eventType; }

    public String getAggregateId() { return aggregateId; }

    public NotificationChannel getChannel() { return channel; }

    public NotificationDeliveryStatus getStatus() { return status; }

    public int getAttemptCount() { return attemptCount; }

    public String getIdempotencyKey() { return idempotencyKey; }

    public String getErrorMessage() { return errorMessage; }

    public String getRecipientEmail() { return recipientEmail; }

    public String getSubject() { return subject; }

    public String getBody() { return body; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(NotificationDeliveryStatus status) { this.status = status; }

    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}