# Task: Order notifications — Phase B (delivery log + idempotency)

Brief template from `docs/notification-system-guide.md` (Phase B), filled before coding.

## Notification brief: order lifecycle notifications — Phase B

### 1. Requirements

- Trigger (same as Phase A): `notifyOrderConfirmed` / `notifyOrderShipped` / `notifyRefundRequested`
  / `notifyRefundApproved` / `notifyRefundRejected` from the application services.
- Purpose: prove what was sent/attempted (audit) and prevent a duplicate email if the same event
  is ever triggered twice.
- Channel(s): email only (unchanged).
- Guarantee: best-effort, now observable; idempotent per business event (single attempt).
- Blocking?: **never** — mail failure still logs WARNING and the order commits.
- Done when: every notification writes one row to a delivery log with a unique idempotency key;
  a second attempt for the same event is skipped; ITs prove claim/mark semantics; smoke proves a
  FAILED row appears while the order commits.

### 2. Scale (lab)

- Same as Phase A: low volume, single Liberty instance. A synchronous insert/update per event is fine.

### 3. Contracts

- Port (new, `order-checkout`): `NotificationDeliveryLogPort`.
  - `boolean claim(NotificationDelivery)` → insert-if-absent (`ON CONFLICT DO NOTHING`); true if newly claimed.
  - `void updateStatus(String idempotencyKey, NotificationDeliveryStatus status, String errorMessage)`.
- Idempotency key format: `ORDER_CONFIRMED:{orderId}`, `REFUND_REQUESTED:{orderId}`, ...
- Payload fields: `eventType`, `aggregateId` (order id), `channel` (EMAIL), `status`, `attemptCount`,
  `errorMessage`, `createdAt`/`updatedAt`.

### 4. Data

- Delivery log table? yes — Flyway `V26__notification_delivery_log.sql`, table
  `tb_notification_delivery_log`, unique index on `idempotency_key`.
- Preference source? unchanged (`UserProfile.notificationsEnabled`; disabled/failed lookup → send).
- Idempotency key format: `EVENT:{orderId}`; uniqueness enforced by a DB constraint.

### 5. Architecture

- Domain `NotificationDelivery` + `NotificationDeliveryLogPort` in `order-checkout`; JPA entity +
  repository in `adapter/out/persistence`; email adapter injects the port and runs
  claim → send → markSent/markFailed inside the same business transaction.
- Schema for the Testcontainers ITs is `drop-and-create` from the JPA entity, so the entity
  carries the same unique constraint as the migration.

### 6. Failure behaviour

- Provider down → `markFailed(key, error)` + log WARNING; order commits (unchanged).
- Duplicate attempt (same key) → `claim` returns false → skip silently (log FINE).
- Preference disabled → no claim, no row (email off is not an attempted delivery).

### 7. Trade-offs

- Choice: synchronous delivery-log write via `INSERT ... ON CONFLICT DO NOTHING` (positional `?N`
  params, per lesson #30) instead of an outbox table.
- Why: audit + idempotency with zero new infrastructure; exact-once-per-event on a single writer.
- Cost: log rows are transactionally coupled to checkout (a checkoute rollback also removes the
  claim); no retry/outbox — that is Phase C, only if the human asks for decoupling.