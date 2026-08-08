# Reviews & Ratings — Implementation Sequence (As-Built)

**Companion docs:** `reviews-ratings-module-spec.md` · `reviews-ratings-backlog.md`
**Release:** [v0.10.0](../docs/releases/v0.10.0.md) (2026-08-05)

This document records the **actual delivery order**.
The original pre-build step list is preserved in git history of this file.

---

## Guiding principles used

- New top-level Maven module: **`product-reviews`**
- Hexagonal layout under `com.loja.productreviews`
- Cross-module access **only** via ports (`product-catalog`, `order-checkout`)
- DTOs in `application/dto` (never nested in ports — ArchUnit)
- Verified purchase = **badge**, not a hard gate on submit
- Admin UI under `/admin-dashboard/reviews/` with `@RolesAllowed("ADMIN")`
- Browser smoke required after WAR wiring (lesson #19)

---

## Actual delivery sequence

### Phase 1 — Module foundation

| Step | Deliverable                                                                                          |
| ---- | ---------------------------------------------------------------------------------------------------- |
| 1    | Maven module skeleton + root POM wiring                                                              |
| 2    | Domain: `Review`, `Rating`, `ReviewStatus`, exceptions; pure unit tests                              |
| 3    | Ports in/out + application DTOs                                                                      |
| 4    | `ReviewApplicationService` (mocked ports)                                                            |
| 5    | JPA entity/mapper/adapter + Flyway **`V17__product_reviews.sql`** + unique `(author_id, product_id)` |
| 6    | Thin adapters: `ProductLookupAdapter`, `OrderVerificationAdapter` (CONFIRMED/SHIPPED/DELIVERED)      |

---

### Phase 2 — Public flow

| Step | Deliverable                                                                                |
| ---- | ------------------------------------------------------------------------------------------ |
| 7    | Submit / summary / list approved use cases wired                                           |
| 8    | Public bean + reviews section on `product-detail.xhtml` (stars, histogram, form, messages) |
| 9    | Register `ReviewJpaEntity` in `web` `persistence.xml`                                      |

---

### Phase 3 — Admin moderation + quality

| Step | Deliverable                                                      |
| ---- | ---------------------------------------------------------------- |
| 10   | Admin list + detail pages; approve / reject with reason          |
| 11   | `web.xml` constraint + `AdminAccessControlCoverageTest` extended |
| 12   | `ReviewHexagonalArchitectureTest` (8 rules); nested DTOs fixed   |
| 13   | Full suite green (85 tests); end-to-end smoke                    |

**Outcome:** v0.10.0 tagged; full submit → moderate → public visibility path works.

---

## What was deliberately _not_ done

| Planned / optional                  | Reality                |
| ----------------------------------- | ---------------------- |
| Block non-purchasers from reviewing | Badge only             |
| Notification on approve/reject      | Deferred               |
| Author “my reviews” / edit UI       | No customer screen yet |
| Review media, votes, vendor replies | Out of scope           |
| Rating on catalog cards             | Product-detail only    |

---

## Recommended order for any _new_ work

1. Prefer extending use cases/ports inside `product-reviews`
2. Keep domain free of `jakarta.*`
3. New JPA types → register in `persistence.xml`
4. Update ArchUnit + unit/IT in the same change
5. After UI changes, smoke product-detail + admin reviews (lesson #19: EL getters, Instant formatting, L2 cache)
6. Do not change “badge vs block” verified-purchase policy without human confirmation

---

## Useful commands

```bash
# Fast unit + ArchUnit
mvn -pl product-reviews test -Dtest='*Test' -DfailIfNoTests=false

# ITs
mvn -pl product-reviews test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false

# WAR + run
mvn clean package -pl web -am
./scripts/run-liberty.sh
```

Smoke path:

1. Login → product detail → submit review
2. Admin → `/admin-dashboard/reviews/` → approve
3. Reload product detail → review + histogram visible

---

## Definition of Done (sequence)

- [x] Domain + persistence + public UI
- [x] Admin moderation + RBAC
- [x] ArchUnit + 85 tests + release notes
- [ ] Optional self-service UI and notifications

---

_This is the as-built execution record. For the original strict 12-step pre-implementation plan, see the git history of this file._

```

```
