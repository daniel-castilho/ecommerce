-- S9/S17-S19: refund request workflow. One row per refund request against an
-- order; status flow PENDING -> APPROVED -> PROCESSED, or PENDING -> REJECTED
-- (see RefundStatus). The order itself moves to REFUND_REQUESTED while a request
-- is open and to REFUNDED once the payment reversal is processed.
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5-V15).

CREATE TABLE refund_requests (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP
);

CREATE INDEX idx_refund_requests_order ON refund_requests (order_id);
CREATE INDEX idx_refund_requests_status ON refund_requests (status);
CREATE INDEX idx_refund_requests_created_at ON refund_requests (created_at);
