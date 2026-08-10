# Testing Playbook

**Role:** Write and interpret tests for this Jakarta EE 11 hexagonal monolith (Open Liberty, Testcontainers, ArchUnit).
**Stack constraints:** JUnit 5 + AssertJ + Mockito only — no new test deps without human approval.

Sources: `AGENTS.md` · `docs/lessons.md` · module `*IT` / ArchUnit · epic docs under `tasks/`

---

## Pyramid

1. **Domain unit** — invariants, Money, coupon math, cart rules, state machines
2. **Application unit** — mocked ports; happy path + rejection + idempotency
3. **Adapter IT** — real DB; constraints; `@Version`; Flyway
4. **ArchUnit** — package boundaries
5. **Smoke** — login → flow → check outcome on Liberty

This is not a public JSON API project. Exercise **ports / use cases**; Faces beans only at the edge. Never adapter → adapter across modules.

---

## Mandatory patterns

- IT setup: `TRUNCATE … CASCADE` · writes inside **`inTx`** · `em.clear()` after commit
- Queries: **`getResultList()`**, not `getResultStream()`
- Round-trip `@Version` in JPA mappers
- Instant comparisons: allow small precision tolerance
- English names: `add_whenProductInactive_rejects`, not `test1`

---

## Regression checklist

| Area | Must verify |
|------|-------------|
| **Auth** | Server-side userId; customer blocked from admin |
| **Catalog** | Only ACTIVE in customer flows; safe fallback if unavailable |
| **Cart** | Persist across restart; one line/product; empty cart cannot order; clear only after successful order |
| **Coupons** | Blank = no discount; invalid fails place-order; discount on merchandise only; snapshot on Order; `usedCount` after success only |
| **Checkout** | Totals = lines − discount + shipping ≥ 0; idempotency preserved |
| **Wishlist / reviews** | Ownership by userId; idempotent add where specified |
| **Admin** | Composition only; ADMIN RBAC |

---

## Reading failures

| Class | Signal | First move |
|-------|--------|------------|
| **Logic** | `AssertionError`, wrong total/status | Fix domain/app or wrong expectation |
| **Persistence** | constraint, empty after save | Flyway, `inTx`, `em.clear`, `@Version` |
| **ArchUnit** | rule failed | Restore boundary — do not weaken |
| **CDI / WAR** | unsatisfied bean, missing entity | Producer/qualifier · `persistence.xml` |
| **Faces unit** | NPE session/context | Mock `SessionPort` / Faces as existing tests |
| **Flaky / env** | Docker, port, timeout | Re-run; fix setup — don’t `@Disabled` |
| **Compile** | symbol / module | POM dependency or move DTO |

**Priority when many fail:** compile/ArchUnit → shared-kernel/Money → touched module ITs → app units → checkout+coupon+cart regressions → unrelated modules.

---

## Analyzer reply format

```text
## Summary
Module / test / class (Logic|Persistence|ArchUnit|CDI|Faces|Flaky|Compile)
Cause (one line)

## Fix plan
1. …
2. …

## Verify
mvn -pl <module> test
# then broader package if needed
```

---

## Do not

- Skip or delete tests to green the build
- Broaden ArchUnit allow-lists
- Call another module’s `adapter` package from tests or production code
- Add multi-coupon stack or new libs without a story + human OK

---

## Done when

- [ ] Happy path + one rejection automated for the change
- [ ] IT covers persistence constraints/version when relevant
- [ ] Failure analysis names root cause and smallest fix
- [ ] Smoke steps clear for Liberty when UI is involved
```
