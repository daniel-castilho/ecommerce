# Notification System Guide — Ecommerce Monolith (Loja)

**Audience:** AI Software Engineer agents  
**Purpose:** Practical design & implementation guidance for order-related (and future) notifications  
**Inspiration:** Structured notification-system design practice (preferences → queue → workers → delivery log → idempotency → retry)  
**Critical stance:** Most public “Design a Notification System” answers target FAANG scale (Kafka, Redis, multi-region). **This lab does not.** Copying those designs here is over-engineering and will hurt maintainability.

---

## Project reality (read first)

| Fact | Implication |
|---|---|
| Single Open Liberty instance | No need for partitioned Kafka topics or auto-scaling worker fleets |
| PostgreSQL + Flyway already exist | Prefer DB-backed log / optional outbox over new infrastructure |
| Existing port | `NotificationPort` in `user-account` (`sendWelcomeEmail`, `sendPasswordResetEmail`) backed by `NotificationEmailAdapter` (real Jakarta Mail). **No order-notification port exists yet** |
| Preference flag | `UserProfile.notificationsEnabled` (boolean) already exists in `user-account` — reuse it |
| Channels today | **Email only** (Jakarta Mail). Push/SMS are out of scope unless the human opens that epic |
| Consistency | Checkout must **not** fail because mail is down (best-effort by default) |
| UI | JSF — no real-time WebSocket inbox required for Phase 1 |
| Security / marketing digests | Deferred technical debt |

**Default policy for this lab**

```text
Transactional notifications (order confirmed, cancelled; shipped if that status is added):
  → best-effort email
  → never block the business transaction
  → log attempt outcome
  → simple retry optional later
```

---

## What is in scope vs out of scope

### In scope (when the human asks for notifications)

- Order lifecycle emails: confirmed, cancelled (shipped once that status exists), later payment failures
- Respect `UserProfile.notificationsEnabled` (email on/off, already exists)
- Delivery attempt logging (success / failure / reason)
- Idempotency for the same logical event (avoid double email on retry)
- Follow the existing template: `NotificationPort` + `NotificationEmailAdapter` in `user-account`

### Out of scope (do not implement “because the blog said so”)

- Kafka / RabbitMQ / Redis Streams as default
- Push (FCM/APNs), SMS (Twilio)
- Marketing campaigns, digests, frequency caps, quiet hours (unless explicitly requested)
- Multi-tenant, multi-region, 30k QPS designs
- Exactly-once global delivery guarantees without a clear product need
- Building a full in-app notification center UI in the first iteration

---

## Design brief template (fill before coding)

Use this when implementing or evolving notifications:

```text
## Notification brief: <event name>

### 1. Requirements
- Trigger (domain event / use-case step):
- Channel(s): email only | + in-app later
- Guarantee: best-effort | at-least-once with idempotency
- Blocking?: never | only for critical security (not used yet)
- Done when:

### 2. Scale (lab)
- Expected volume: low (manual / few concurrent checkouts)
- Peak assumption: tens of emails/min is enough

### 3. Contracts
- Domain/application event or method:
- OrderNotificationPort methods:
- Payload fields (orderId, customerId, totals, tracking…):

### 4. Data
- Delivery log table? yes/no
- Preference source? none | user preference flag
- Idempotency key format:

### 5. Architecture
- Sync call from application service | outbox | JMS later
- Adapter package:

### 6. Failure behaviour
- Provider down → log FAILED, do not rollback order
- Retry policy (if any):

### 7. Trade-offs
- Choice / Why / Cost:
```

---

## Recommended architecture for this repository

### Phase A — Minimal (default, start here)

```text
CheckoutService.checkout() / Order.confirm() / Order.cancel()
    (after successful domain transition)
        → OrderNotificationPort.notifyXxx(order)   // new port in order-checkout
            → OrderNotificationEmailAdapter        // mirrors NotificationEmailAdapter
```

- No new queue.
- No new module unless volume of code forces it.
- Checkout / order status transitions **always commit** even if mail throws (catch & log inside adapter or application boundary).

### Phase B — Observable (when you need audit)

Add a **DeliveryLog** (Flyway migration):

| Column | Purpose |
|---|---|
| id | PK |
| event_type | e.g. ORDER_CONFIRMED |
| aggregate_id | order id |
| channel | EMAIL |
| status | PENDING / SENT / FAILED |
| attempt_count | |
| idempotency_key | unique |
| error_message | nullable |
| created_at / updated_at | |

Unique constraint on `idempotency_key` (or `event_type + aggregate_id + channel`).

Adapter flow:

1. Compute key: `ORDER_CONFIRMED:{orderId}`  
2. Insert or skip if already SENT  
3. Send mail  
4. Update status  

### Phase C — Async (only if human requests real decoupling)

Options **in order of simplicity**:

1. **Transactional outbox** table + scheduled Liberty task that polls and sends  
2. Introduce Jakarta Messaging (Liberty `messaging` feature + a JMS resource) **only with explicit human approval** — there is no JMS today  
3. External broker — **reject** unless justified in writing

Do **not** introduce Kafka “because every notification blog uses Kafka.”

---

## Core contracts (hexagonal)

Ports live in `domain/port/out` per module; domain stays pure (zero `jakarta.*`). The order port below does **not** exist yet — create it in `order-checkout`.

```text
OrderNotificationPort (to be created)
  notifyOrderConfirmed(Order order)
  notifyOrderShipped(Order order, String trackingNumber)
  notifyOrderCancelled(Order order, String reason)
```

Optional later — read the flag through the existing user-account lookup instead of a new port:

```text
existing user lookup (UserRepositoryPort)
  isEmailEnabled(userId)   // UserProfile.notificationsEnabled
```

Payload should use domain data already on `Order` (id, customerId, total, lines snapshot, tracking, cancellation reason). Do not pass JPA entities or Faces beans into the port.

---

## Data model guidelines

**Prefer**

- Snapshot fields needed for the email body at send time (order total, product names) so later catalog changes do not rewrite history  
- Idempotency key derived from business event, not random UUID alone  

**Avoid**

- Storing full rendered HTML in the domain  
- A giant generic `notifications` table for every product feature before User Account exists  
- Quiet hours / timezone / frequency caps before product asks for them  

**Preferences (already exists)**

`user-account` already exposes `UserProfile.notificationsEnabled` (boolean). Reuse it; do not add a new table/column.

Everything else is progressive enhancement.

---

## Reliability patterns (lab-sized)

| Pattern | Lab recommendation |
|---|---|
| At-most-once | Acceptable for marketing; **not** ideal for “order shipped” |
| At-least-once + idempotency | Preferred for transactional email |
| Exactly-once | Do not promise; expensive and rarely needed here |
| Retry | 0–3 attempts with simple backoff inside adapter; then FAILED |
| DLQ | Optional table flag or status=DEAD; no separate Kafka DLQ |
| Provider failover | Single SMTP for now; document as future debt |

**Critical rule:**  
Notification failure must **not** roll back `CheckoutService.checkout()` or `Order.confirm()`/`Order.cancel()` unless the human explicitly requires synchronous guaranteed delivery.

---

## Mapping to current codebase

| Existing piece | Role |
|---|---|
| `NotificationPort` + `NotificationEmailAdapter` (user-account) | Real Jakarta Mail template to mirror for orders |
| No order-notification port yet | Create `OrderNotificationPort` in order-checkout `domain/port/out` |
| `Order` status transitions (`confirm`/`cancel`; OPEN/CONFIRMED/CANCELLED) | Natural trigger points |
| Jakarta Mail on Liberty (`mail-2.1`, `java:app/env/mail/Session`) | Real email adapter |
| `UserProfile.notificationsEnabled` | Preference flag (already exists) |
| Flyway | Any `notification_delivery_log` migration |
| No JMS/MDB in the project | Introduce messaging only with explicit approval (Phase C) |

Do not create a parallel “NotificationService” that bypasses ports and talks to SMTP from the JSF layer.

---

## Implementation sequence (assertive)

When the human opens a notification task:

1. Write the brief (template above).  
2. Create `OrderNotificationPort` in `order-checkout` (mirror `NotificationPort`).  
3. Ensure application service calls the port **after** successful domain transition and **outside** the failure path of the business transaction (or catch inside adapter).  
4. Implement/adjust adapter (logging first, then mail).  
5. If audit required → Flyway `V*__notification_delivery_log.sql` + uniqueness on idempotency key.  
6. Unit test: port called once per transition; duplicate key does not send twice.  
7. Manual smoke: place order → confirm → check log/mail.  
8. Update `docs/lessons.md` with the chosen guarantee and trade-off.

Stop after each phase unless the prompt says to continue.

---

## Anti-patterns (reject these)

- Designing for 1B users / 5k QPS in this EAR  
- Adding Redis only for a dedup key when a unique DB constraint works  
- Sending email inside a JPA `@PrePersist` or domain entity  
- Catching `Exception` and swallowing without log  
- Dual write: EJB path sends mail and port path also sends mail  
- Building push/SMS “for completeness”  
- Making checkout depend on SMTP availability  

---

## Evolution triggers (when to leave Phase A)

| Trigger | Move to |
|---|---|
| Need proof of what was sent | Phase B (delivery log) |
| SMTP latency slows request threads | Phase C (outbox or JMS) |
| Duplicate emails after retries | Idempotency key + unique constraint |
| User asks for opt-out | Preference flag on user |
| Human asks for push/SMS | New epic; new channel adapters only |

---

## Relationship to other docs

| Doc | Use with this guide |
|---|---|
| `docs/design-thinking-framework.md` | Run the 8 steps; this file specialises step 3–7 for notifications |
| `README.md` | Module boundaries (ports live in `domain/port/out`) |
| `AGENTS.md` | Zero framework in domain/application; no Kafka/Redis without approval |
| `docs/lessons.md` | Record chosen guarantee and failures |

---

## Definition of done (notification slice)

- [ ] Brief filled (guarantee + channel + non-blocking rule)  
- [ ] Port method(s) defined; no framework types on port  
- [ ] Application service invokes port at the correct lifecycle point  
- [ ] Adapter does not break the business transaction on mail failure  
- [ ] Idempotency defined if retries exist  
- [ ] Flyway migration only if log/preferences tables are required  
- [ ] `mvn -q -pl order-checkout -am compile` (and `user-account`) succeeds  
- [ ] Smoke path documented (which email, which log row)  
- [ ] Trade-off one-liner in `docs/lessons.md`  

---

## One-line summary for agents

**Start with a non-blocking email adapter behind `OrderNotificationPort`; add a delivery log and idempotency when you need audit; add a queue only when latency or volume forces it. Never import a Big-Tech notification architecture wholesale into this lab.**

---
