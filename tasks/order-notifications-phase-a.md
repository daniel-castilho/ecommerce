# Task: Order notifications — Phase A (best-effort email)

Brief template from `docs/notification-system-guide.md`, filled before coding.

## Notification brief: order lifecycle notifications

### 1. Requirements

- Trigger (domain event / use-case step):
  - `OrderApplicationService.checkout` → after successful `order.confirm()` + save (already calls `NotificationPort.notifyOrderConfirmed`).
  - `OrderHistoryService.requestRefund` → after refund requested + save (already calls `notifyRefundRequested`).
  - `RefundApplicationService.approveRefund` / `rejectRefund` → after transition + save (already call `notifyRefundApproved` / `notifyRefundRejected`).
  - `notifyOrderShipped` has no caller yet (`shipped` status not in use); the email adapter implements it anyway to keep the port complete.
- Channel(s): email only (Jakarta Mail). Push/SMS out of scope.
- Guarantee: best-effort. No queue, no delivery log, no retries in Phase A.
- Blocking?: **never** — SMTP failure must not roll back the order transaction.
- Done when: order commits even with the mail server down; preference flag respected; only the email adapter is CDI on the port; unit tests cover message builder + non-blocking adapter.

### 2. Scale (lab)

- Expected volume: low (manual / few concurrent checkouts).
- Peak assumption: tens of emails/min is enough for the single Liberty instance.

### 3. Contracts

- Domain/application event or method: `NotificationPort` (already exists in `order-checkout`), methods `notifyOrderConfirmed`, `notifyOrderShipped`, `notifyRefundRequested`, `notifyRefundApproved`, `notifyRefundRejected` — each `throws NotificationException` (adapter never throws).
- Payload fields used: `order.getId()`, `order.getCustomerEmail()`, `order.getTotal()`, `order.getItems()` (product name, quantity, line total), `order.getTrackingNumber()`, `shipped.trackingNumber()`, `RefundRequest` (amount, reason, rejectionReason).

### 4. Data

- Delivery log table? no (Phase B when audit is needed).
- Preference source? `UserProfile.notificationsEnabled` via `FindUserUseCase.findById(userId)`; missing user or failed lookup → send (default-on).
- Idempotency key format: n/a (Phase A, single attempt).

### 5. Architecture

- Sync call from the application service (already in place) → `NotificationPort` → `OrderNotificationEmailAdapter` (mirrors `NotificationEmailAdapter` in `user-account`).
- Adapter package: `order-checkout/.../adapter/out/notification`.
- The current `NotificationMockAdapter` loses `@ApplicationScoped` (stays as a test helper constructed via `new`) so the email adapter is the sole CDI bean on the port.

### 6. Failure behaviour

- Provider down → catch `MessagingException`/`RuntimeException`, log WARNING, return; order transaction commits.
- Preference lookup failure → log FINE, default to send.
- Retry policy: none in Phase A.

### 7. Trade-offs

- Choice: best-effort synchronous email from the application service.
- Why: matches lab scale, zero infra, checkout is never coupled to SMTP.
- Cost: no delivery audit, possible silent loss (mitigated by WARNING log); mail adds latency to the request thread.