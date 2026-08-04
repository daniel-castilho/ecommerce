package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refund_requests")
public class RefundRequestJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected RefundRequestJpaEntity() {}

    public RefundRequestJpaEntity(String id, String orderId, BigDecimal amount, String reason, RefundStatus status, String rejectionReason, Instant createdAt, Instant processedAt) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
}
