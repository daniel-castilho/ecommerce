# Design Thinking Framework — Online Shop Lab

**Audience:** AI Software Engineer agents working in this repository
**Purpose:** Structured method to design or evolve features before writing code
**Source inspiration:** Systematic system-design practice (clarify → estimate → contracts → data → architecture → bottlenecks → trade-offs → evolve)
**Scope:** Jakarta EE 11 + Open Liberty + hexagonal modules (user-account, product-catalog, order-checkout).

---

## When to use this document

Use this framework **before** implementing:

- A new bounded context or module (e.g. full User Account, Payments, Notifications)
- A non-trivial change to Order, Cart, Catalog, or messaging
- Any async path (email, retry, delivery guarantees, future queue/outbox)
- Schema changes that affect more than one table

Do **not** use it for:

- Typo fixes, import cleanups, pure refactors with no behavior change
- Doc-only alignment tasks already specified in a cleanup prompt

---

## Hard constraints of this project

Agents must respect these while applying the framework:

| Constraint   | Rule                                                                           |
| ------------ | ------------------------------------------------------------------------------ |
| Runtime      | Open Liberty only                                                              |
| APIs         | `jakarta.*` only; no new `javax.*`                                             |
| Architecture | Hexagonal: domain ← application ← adapters ← web                               |
| Web UI       | Jakarta Faces (JSF); no SPA rewrite                                            |
| Persistence  | Flyway is schema source of truth; JTA `jdbc/EcommerceDS`                      |
| New services | CDI `@ApplicationScoped` + `@Transactional`; **zero** `@EJB`/`@Stateless`/DAO  |
| Security     | Deferred technical debt unless the human explicitly opens that epic            |
| Dual paths   | Never introduce a second write path for the same aggregate                     |

---

## The 8 steps (execute in order)

### 1. Clarify the requirements

**Goal:** Avoid building the wrong thing.

Ask and answer in writing (short bullets):

- **Functional:** What must the user/system be able to do?
- **Non-functional:** Latency expectations (lab-scale is fine), consistency needs, failure behavior
- **Actors:** Guest, CUSTOMER, ADMIN (VENDOR only if in scope)
- **In scope / out of scope** for _this_ epic
- **Success criteria:** How do we know it works (page flow, DB row, log line, test name)?

**Project examples:**

| Feature            | Clarify first                                                         |
| ------------------ | --------------------------------------------------------------------- |
| Order notification | Email only or also in-app? Best-effort or guaranteed? Mock OK in dev? |
| User login         | Session-based only (Phase 1)? Roles CUSTOMER/ADMIN only?              |
| Stock on checkout  | Decrement when? Reserve vs confirm? Concurrent carts?                 |

**Output of this step:** a 5–15 line “Requirements” block in the PR/commit notes or in `docs/lessons.md` if the decision is lasting.

---

### 2. Estimate the scale (lab-realistic)

**Goal:** Choose simplicity appropriate to a learning EAR, not Netflix.

Default assumptions for this lab unless the human says otherwise:

- Single Open Liberty instance
- Tens to hundreds of products; low concurrent users
- PostgreSQL on Docker; LocalStack for S3

Still do a **tiny** estimate when async or storage is involved:

- Expected writes/day for orders or notifications
- Whether a DB table is enough vs a queue
- Retention (e.g. keep delivery logs 30 days vs forever)

**Rule:** Prefer the simplest design that meets clarified requirements. Do not add Kafka, Redis, or microservices unless explicitly requested.

---

### 3. Define the contracts (APIs / ports)

**Goal:** Fix the interface before the internals.

For each use case, define:

- **Inbound port** (application): method names, inputs, outputs, domain exceptions
- **Outbound ports** needed: repository, mail, payment, inventory, session, audit
- **Web outcomes** (JSF): which bean method, which navigation string, which FacesMessage on error

**Example shape (Order notification):**

```text
Inbound:
  notifyOrderConfirmed(OrderId id)
  notifyOrderShipped(OrderId id, trackingNumber)

Outbound:
  OrderNotificationPort.send(OrderNotification)
  OrderRepositoryPort.findById(...)
```

No framework types on inbound/outbound port signatures in domain/application layers.

---

### 4. Design the data model

**Goal:** Make queries and invariants obvious.

- Prefer domain language (Order, OrderLine, User, Address)
- Map to Flyway tables only after domain shapes are clear
- Note indexes only where a real query needs them (`userId`, `status`, `created_at`)
- Document soft-delete vs hard-delete

**If adding notification support, consider:**

| Concept                  | Why                                         |
| ------------------------ | ------------------------------------------- |
| Delivery attempt log     | status, channel, attemptCount, lastError    |
| User contact/preferences | email enabled, quiet hours (optional later) |
| Idempotency key          | avoid duplicate emails on retry             |

Do not create tables “just in case.”

---

### 5. Sketch high-level architecture (hexagonal)

**Goal:** Place each responsibility in the correct module.

```text
web (JSF, WAR)
    → user-account / product-catalog / order-checkout (use cases)
        → domain/port (interfaces)
            → adapter/* per module (JPA, S3, mail)
                → PostgreSQL / LocalStack / EcommerceMail (SMTP)
```

Checklist:

- [ ] Domain has no `jakarta.*`
- [ ] Application depends only on domain + port interfaces
- [ ] Adapters implement ports; web does not call JPA entities
- [ ] Adapters are the only write path; no second path to the same aggregate

**Async pattern (when needed):**

```text
Use case persists state
  → publishes intent to queue (or calls NotificationPort)
    → worker/adapter delivers email/SMS
      → writes DeliveryLog
```

Start with a **sync adapter behind a port** (the `NotificationEmailAdapter` pattern); add a queue/outbox only when the human asks for real async.

---

### 6. Identify bottlenecks (lab edition)

List where it breaks **in this environment**, not at 30k QPS:

- Liberty restart loses in-memory session cart
- Single DB connection pool exhaustion under bad queries
- LocalStack down → image upload fails (already mitigated with lazy S3 client)
- SMTP misconfig → checkout should not hard-fail if notification is best-effort
- Adapter bypassing its port → inconsistent state (e.g. writing stock outside `CheckoutService`)

Document mitigations in one line each.

---

### 7. Discuss trade-offs explicitly

Every non-obvious choice gets:

```text
Choice: ...
Why: ...
Cost: ...
```

**Examples relevant to this repo:**

| Choice                  | Why                                              | Cost                                     |
| ----------------------- | ------------------------------------------------ | ---------------------------------------- |
| Best-effort order email | Simpler checkout; lab focus is domain/order flow | User may miss email                      |
| Session cart            | Fits JSF; no cart table yet                      | Lost on session expiry                   |
| Flyway over hbm2ddl     | Repeatable schema                                | Must write migrations                    |
| No checkout email yet   | Checkout stays transactional; email can be added behind a port later | No email to the customer today |
| Security deferred       | Stabilize catalog/order first                    | No real RBAC yet                         |

---

### 8. Evolve under new constraints

When the human adds a requirement mid-epic:

1. Return to step 1 (what changed?)
2. Touch only the affected steps (data model, ports, adapters)
3. Do **not** redesign the whole module from scratch
4. Keep changes compilable (`mvn -q -pl <module> -am compile`)

**Example:** “Guaranteed email delivery” implies idempotency key, retry policy, delivery log, and checkout that still succeeds if mail is down (or explicit failure policy agreed in step 1).

---

## Mapping framework → this repository

| Framework step | Where it lands in the repo                                          |
| -------------- | ------------------------------------------------------------------- |
| Clarify        | Epic prompt / `README.md` (Current state) / `tasks/*.md` / PR note   |
| Estimate       | README or lessons only if it affects design                         |
| Contracts      | `domain/port/in` + `domain/port/out` per module                     |
| Data model     | `domain/model` + `flyway/sql/V*.sql` + JPA entities in adapters     |
| Architecture   | Module boundaries + adapter packages                                |
| Bottlenecks    | `docs/lessons.md`                                                   |
| Trade-offs     | `docs/lessons.md` or epic report                                    |
| Evolve         | Follow-up commit; no big-bang rewrite                               |

---

## Mini template (copy into agent report before coding)

```text
## Design brief: <feature name>

### 1. Requirements
- Functional:
- Non-functional:
- In scope:
- Out of scope:
- Done when:

### 2. Scale assumptions
- ...

### 3. Contracts
- Inbound ports:
- Outbound ports:
- JSF entry points:

### 4. Data model
- New/changed tables:
- Domain invariants:

### 5. Architecture
- Modules touched:
- Sync vs async:

### 6. Bottlenecks / risks
- ...

### 7. Trade-offs
- Choice / Why / Cost:

### 8. Implementation order
1. Domain + tests
2. Ports
3. Flyway + JPA adapter
4. Application service
5. JSF bean + page
6. mvn compile + manual smoke path
```

---

## Anti-patterns (do not do)

- Jump to Kafka/Redis/microservices for a lab shop
- Code adapters before ports and domain invariants
- Mix `javax.*` and `jakarta.*`
- Implement security “while at it”
- Leave Product/Cart updates on both DAO and RepositoryPort
- Memorize a Big Tech blog design and force it onto this EAR

---

## Relationship to other docs

| Doc                            | Role                                              |
| ------------------------------ | ------------------------------------------------- |
| `AGENTS.md`       | Working rules for agents                          |
| `README.md`       | Module map (hexagonal), build/run commands         |
| `docs/lessons.md` | Decisions and failures already learned            |
| `tasks/*.md`      | Epic specs, implementation sequences, backlogs    |
| **This file**     | **How to think before building the next feature** |

---

## Definition of ready for an AI Software Engineer

Before writing implementation code for a non-trivial feature, the agent should have completed steps 1–7 in the mini template (even briefly). Step 8 is the coding sequence.

If requirements are unclear, **stop and ask the human** — do not guess product scope.

---
