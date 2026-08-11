# Task: Order notifications — Phase C (transactional outbox + scheduled dispatch)

Brief per `docs/notification-system-guide.md` (Phase C, option 1), filled before coding.

> **Status: DONE (2026-08-10).** V27 applied to the real DB (registered rank 27); smoke order
> `3de0a50a` CONFIRMED with the outbox row claimed PENDING (snapshot present) and flipped FAILED
> (attempt 3) by the background poller (~5 s ticks, threads `000000a0`/`000000fc` — not the request
> thread); retries stopped at attempt 3. Unit tests + `NotificationDeliveryLogRepositoryIT` (11)
> green. Docs: `README.md`, `docs/lessons.md` #33.

## Notification brief: order notifications — Phase C (async outbox)

### 1. Requirements

- Trigger (same as Phase A/B): the port methods called from the application services.
- Goal: remove SMTP entirely from the request thread. Checkout claims a PENDING outbox row in its
  own transaction and returns; a scheduled Liberty task dispatches the email asynchronously.
- Channel(s): email only (unchanged).
- Guarantee: at-least-once dispatch with idempotency (unique `idempotency_key`) + limited retry
  (up to 3 attempts). Best-effort delivery: a failure is recorded, never retried forever.
- Blocking?: **never** — neither checkout nor the poller fails a business transaction.
- Done when: checkout writes a PENDING row (with subject/body/recipient snapshot) and returns; the
  poller flips it to SENT/FAILED; retries stop at 3; a smoke shows PENDING → FAILED with the order
  committed and SMTP down.

### 2. Scale (lab)

- Same as before: low volume, single Liberty instance, single poller.

### 3. Contracts

- Port additions: `NotificationDeliveryLogPort.findDue(int limit)` → PENDING or FAILED rows with
  `attempt_count < 3`, oldest first (the dispatch queue).
- Idempotency key format unchanged: `EVENT:{orderId}`.
- Outbox payload snapshot (new V27 columns on the existing delivery-log table):
  `recipient_email`, `subject`, `body` — rendered at claim time from the in-hand Order/RefundRequest
  so history never rewrites (order lines are already snapshots on the order).

### 4. Data

- No new table: `tb_notification_delivery_log` (V26) becomes the transactional outbox + audit log.
- V27: `ALTER TABLE` adds `recipient_email`, `subject`, `body`.
- Idempotency: unchanged unique index on `idempotency_key`.

### 5. Architecture

- Adapter (`OrderNotificationEmailAdapter`): preference gate → render draft → `claim` (INSERT …
  ON CONFLICT DO NOTHING) with the snapshot → return. No `Session`, no `Transport` anymore.
- `NotificationOutboxProcessor` (@ApplicationScoped): `findDue(50)` → for each, build a MimeMessage
  from the snapshot and `Transport.send`; `updateStatus` SENT or FAILED(+attempt). `@Transactional`.
- `NotificationOutboxDispatcher` (@ApplicationScoped): `@PostConstruct` schedules a fixed-delay
  poll (5 s) on `ManagedScheduledExecutorService` (`java:comp/DefaultManagedScheduledExecutorService`,
  `concurrent-3.1` feature) → calls the processor. No EJB timer (AGENTS: zero `@EJB`).

### 6. Failure behaviour

- SMTP down → row FAILED + attempt++ ; next polls retry while `attempt_count < 3`, then stop.
- Duplicate claim → `claim` false → skip.
- Preference disabled → no row (nothing to dispatch).

### 7. Trade-offs

- Choice: delivery-log-as-outbox + snapshot payload + scheduled poll.
- Why: decouples SMTP latency from checkout with zero new infrastructure (reuses V26 table and the
  `concurrent-3.1` feature already installed).
- Cost: at-least-once (a crash between send and `markSent` can re-send; idempotency key only guards
  duplicate events, not duplicates across a crash window); up to 5 s dispatch latency; snapshot
  duplicates order data in the outbox row.