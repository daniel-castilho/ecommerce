package com.loja.ordercheckout.domain.model;

import com.loja.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

public class RefundRequest {
    private final String id;
    private final String orderId;
    private final Money amount;
    private final String reason;
    private RefundStatus status;
    private String rejectionReason;
    private final Instant createdAt;
    private Instant processedAt;

    private RefundRequest(String id, String orderId, Money amount, String reason, RefundStatus status, String rejectionReason, Instant createdAt, Instant processedAt) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public static RefundRequest request(String orderId, Money amount, String reason) {
        if (amount == null || amount.getAmount().doubleValue() <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Refund reason is required");
        }
        return new RefundRequest(UUID.randomUUID().toString(), orderId, amount, reason, RefundStatus.PENDING, null, Instant.now(), null);
    }

    public static RefundRequest reconstitute(String id, String orderId, Money amount, String reason, RefundStatus status, String rejectionReason, Instant createdAt, Instant processedAt) {
        return new RefundRequest(id, orderId, amount, reason, status, rejectionReason, createdAt, processedAt);
    }

    public void approve() {
        if (status != RefundStatus.PENDING) {
            throw new IllegalStateException("Can only approve PENDING refunds");
        }
        this.status = RefundStatus.APPROVED;
    }

    public void markAsProcessed() {
        if (status != RefundStatus.APPROVED) {
            throw new IllegalStateException("Can only process APPROVED refunds");
        }
        this.status = RefundStatus.PROCESSED;
        this.processedAt = Instant.now();
    }

    public void reject(String rejectionReason) {
        if (status != RefundStatus.PENDING) {
            throw new IllegalStateException("Can only reject PENDING refunds");
        }
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        this.status = RefundStatus.REJECTED;
        this.rejectionReason = rejectionReason;
        this.processedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public Money getAmount() { return amount; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
}
