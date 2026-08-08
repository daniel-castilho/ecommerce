# Feature Design Framework — Online Shop Lab

**Audience:** AI Software Engineer agents working in this repository
**Purpose:** Structured method to design or evolve features **before** writing code
**Nature:** System / feature design for this codebase (not classical UX “Design Thinking”)
**Stack:** Jakarta EE 11 · Open Liberty · hexagonal multi-module monolith

---

## When to use

**Use before implementing:**

- A new bounded context or module
- A non-trivial change to catalog, orders, users, reviews, admin, or messaging
- Any async path (email, retry, delivery guarantees, queue/outbox)
- Schema changes that touch more than one table

**Do not use for:**

- Typos, import cleanups, pure refactors with no behavior change
- Doc-only alignment already specified in a cleanup prompt

---

## Hard constraints

| Constraint   | Rule                                                                          |
| ------------ | ----------------------------------------------------------------------------- |
| Runtime      | Open Liberty only (`./scripts/run-liberty.sh`)                                |
| APIs         | `jakarta.*` only; no new `javax.*`                                            |
| Architecture | Hexagonal: domain ← application ← adapters; web is the WAR host               |
| Web UI       | Jakarta Faces (JSF); no SPA rewrite                                           |
| Persistence  | Flyway is schema source of truth; JTA `jdbc/EcommerceDS`                      |
| Services     | CDI `@ApplicationScoped` + `@Transactional`; zero `@EJB` / `@Stateless` / DAO |
| Security     | **Extend existing Jakarta Security RBAC** — do not invent a second auth model |
| Write paths  | Never introduce a second write path for the same aggregate                    |
| Dependencies | No new Maven dependency without explicit human approval                       |
| Language     | English only (code, comments, commits, docs)                                  |

---

## The 8 steps (in order)

### 1. Clarify the requirements

**Goal:** Avoid building the wrong thing.

Answer in short bullets:

- **Functional:** What must the user/system be able to do?
- **Non-functional:** Consistency, failure behavior, latency (lab-scale is fine)
- **Actors:** Guest, CUSTOMER, ADMIN (VENDOR only if in scope)
- **In scope / out of scope** for _this_ epic
- **Done when:** page flow, DB row, test name, or smoke path

**Output:** a short Requirements block in the epic notes under `tasks/` (or the PR description).
Durable engineering rules go to `docs/lessons.md` — not every epic requirement.

### 2. Estimate the scale (lab-realistic)

**Default assumptions** unless the human says otherwise:

- Single Open Liberty instance
- Tens to hundreds of products; low concurrency
- PostgreSQL (Docker); LocalStack for S3

Tiny estimate when async or storage is involved: writes/day, table vs queue, retention.

**Rule:** Prefer the simplest design that meets the requirements.
Do **not** add Kafka, Redis, Elasticsearch, or microservices unless explicitly requested.

### 3. Define the contracts (ports)

**Goal:** Fix the interface before the internals.

For each use case:

- **Inbound ports:** method names, inputs, outputs, domain exceptions
- **Outbound ports:** repository, mail, payment, inventory, session, audit, …
- **Web outcomes:** bean method, navigation, FacesMessage on error

No framework types on domain/application port signatures.
DTOs live in `application/dto` — never nested inside port interfaces.

### 4. Design the data model

- Prefer domain language first; map to Flyway only after shapes are clear
- Indexes only where a real query needs them
- Soft-delete vs hard-delete stated explicitly
- Do not create tables “just in case”

### 5. Sketch high-level architecture (hexagonal)

```text
web (JSF, WAR)
  → user-account | product-catalog | order-checkout | product-reviews | admin-dashboard
      → domain/port (interfaces)
          → adapter/* (JPA, S3, mail, mocks)
              → PostgreSQL / LocalStack / SMTP
```

````

Checklist:

- [ ] Domain has no `jakarta.*` / `javax.*`
- [ ] Application depends only on domain + port interfaces
- [ ] Adapters implement ports; web does not touch JPA entities
- [ ] One write path per aggregate
- [ ] Cross-module access only via ports

**Async:** start with a **sync adapter behind a port**; add queue/outbox only when the human asks.

### 6. Identify bottlenecks (lab edition)

Examples relevant here:

- Session cart lost on Liberty restart
- Connection pool exhaustion from bad queries
- LocalStack down → image upload fails
- SMTP misconfig → notification should be best-effort unless step 1 said otherwise
- Bypassing a port → inconsistent aggregate state

One-line mitigation each; promote lasting ones to `docs/lessons.md`.

### 7. Discuss trade-offs explicitly

```text
Choice: ...
Why: ...
Cost: ...
```

| Choice                       | Why                           | Cost                       |
| ---------------------------- | ----------------------------- | -------------------------- |
| Best-effort email            | Checkout stays simple         | User may miss the message  |
| Session cart                 | Fits JSF without a cart table | Lost on session expiry     |
| Flyway over hbm2ddl          | Repeatable schema             | Migrations must be written |
| Mock payment/shipping        | Lab focus on domain flow      | Not production payment     |
| Badge-only verified purchase | Reviews still collectable     | Non-buyers can review      |

### 8. Evolve under new constraints

1. Return to step 1 (what changed?)
2. Touch only affected steps
3. Do not redesign the whole module
4. Keep the tree compilable (`mvn -q -pl <module> -am compile`)

---

## Mapping → this repository

| Step                     | Lands in                                        |
| ------------------------ | ----------------------------------------------- |
| Clarify                  | `tasks/*` epic docs / PR note                   |
| Estimate                 | Only if it changes design                       |
| Contracts                | `domain/port/in` + `domain/port/out`            |
| Data model               | `domain/model` + `flyway/sql` + JPA in adapters |
| Architecture             | Module boundaries + adapter packages            |
| Bottlenecks / trade-offs | `docs/lessons.md` when durable                  |
| Evolve                   | Follow-up commit; no big-bang rewrite           |

---

## Mini template (before coding)

```text
## Design brief: <feature name>

### 1. Requirements
- Functional:
- Non-functional:
- In scope / out of scope:
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
2. Ports + DTOs
3. Flyway + JPA adapter
4. Application service
5. JSF bean + page (+ persistence.xml if new entity)
6. ArchUnit + unit/IT
7. mvn package + Open Liberty smoke
```

---

## Anti-patterns

- Jump to Kafka / Redis / microservices / Elasticsearch for a lab shop
- Code adapters before ports and domain invariants
- Mix `javax.*` and `jakarta.*`
- Invent a second authentication mechanism
- Leave two write paths to the same aggregate
- Force a Big Tech blog design onto this monolith
- Put business rules in `admin-dashboard` or in JSF beans

---

## Relationship to other docs

| Doc                     | Role                                                          |
| ----------------------- | ------------------------------------------------------------- |
| `AGENTS.md`             | Working rules for agents                                      |
| `README.md`             | Module map, build/run                                         |
| `docs/lessons.md`       | Durable engineering rules                                     |
| `docs/design-system.md` | UI tokens and rule of two                                     |
| `tasks/*.md`            | Epic specs, backlogs, sequences                               |
| **This file**           | **How to think before building the next non-trivial feature** |

---

## Definition of ready

Before implementation code for a non-trivial feature, complete steps **1–7** of the mini template (even briefly). Step **8** is the coding sequence.

If requirements are unclear, **stop and ask the human** — do not guess product scope.

---

_Earlier versions of this document (including outdated “security deferred” guidance and a narrower module list) remain available in git history._

```

---

You can replace the current content of `docs/design-thinking-framework.md` with this text.

**Optional follow-up:** if you want the filename to match the content, rename to something like `docs/feature-design-framework.md` and update links in `AGENTS.md` / `README.md` — not required for the content to be valid.
```
````
