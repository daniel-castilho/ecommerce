# Reviews & Ratings — Backlog Status

**Companion documents:**
`reviews-ratings-module-spec.md` · `reviews-ratings-implementation-sequence.md`
**Release:** [v0.10.0](../docs/releases/v0.10.0.md) (2026-08-05)

**Epic goal:** Customers leave star ratings and text reviews; product pages show averages and approved reviews; admins moderate a pending queue.

---

## Current Status Summary

| Area                                   | Status          | Notes                                                             |
| -------------------------------------- | --------------- | ----------------------------------------------------------------- |
| Domain model + exceptions (S1)         | ✅ Done         | `Review` state machine, `Rating`, aggregate summary types         |
| Ports + application service (S2–S3)    | ✅ Done         | 8 inbound use cases; thin cross-module adapters                   |
| Persistence + Flyway (S4)              | ✅ Done         | `V17__product_reviews.sql`; unique `(author_id, product_id)`      |
| Submit + verified-purchase flag (S5)   | ✅ Done         | Badge only — does **not** block non-purchasers                    |
| Rating summary + approved list (S6–S7) | ✅ Done         | Average, histogram, pagination                                    |
| Public UI on product-detail (S8)       | ✅ Done         | Stars, histogram (`barChart`), form, FacesMessages                |
| Admin moderation list/detail (S9–S11)  | ✅ Done         | PENDING queue; approve; reject with mandatory reason              |
| ArchUnit + RBAC (S12)                  | ✅ Done         | `ReviewHexagonalArchitectureTest`; admin coverage extended        |
| Full test suite (S13)                  | ✅ Done         | 85 module tests green                                             |
| Hide own review UI (S14)               | ⚠️ Partial      | Use case may exist; **no** dedicated customer “my reviews” screen |
| Notifications on moderate              | ❌ Deferred     | `NotificationPort` not wired                                      |
| Images / voting / vendor replies       | ❌ Out of scope | Explicit debt                                                     |

**MVP (S1–S8 + S12–S13) and admin moderation (S9–S11): delivered in v0.10.0.**

---

## Implemented Stories

### Foundation

- **S1** — `Review` (PENDING → APPROVED / REJECTED / HIDDEN), `Rating` VO, domain exceptions
- **S2** — Inbound use cases + `ReviewRepositoryPort`, `ProductLookupPort`, `OrderVerificationPort`
- **S3** — `ReviewApplicationService` + DTOs in `application/dto`
- **S4** — JPA adapter, unique constraint, aggregate summary query, Flyway `V17` + rollback script

### Public flow

- **S5** — Authenticated submit (title + body, OWASP-sanitized); duplicate blocked
- **S6** — Summary: average, count, 1★–5★ histogram
- **S7** — Paginated APPROVED reviews (newest first)
- **S8** — Integrated into `product-detail.xhtml`

Verified purchase: order must be CONFIRMED / SHIPPED / DELIVERED for the **badge**; submission is still allowed without purchase.

### Admin moderation

- **S9** — `/admin-dashboard/reviews/list.xhtml` (PENDING queue, pagination)
- **S10** — Approve
- **S11** — Reject with required reason

### Quality

- **S12** — ArchUnit (8 rules; nested DTOs moved to `application/dto`) + ADMIN RBAC
- **S13** — Domain + service + bean unit tests + adapter ITs

---

## Still Pending / Explicit Debt

| Item                                                           | Notes                                            |
| -------------------------------------------------------------- | ------------------------------------------------ |
| Author “my reviews” / edit UI                                  | Backend capability may exist; no customer screen |
| Moderation email notifications                                 | Deferred with notification module                |
| Review media, helpfulness votes, vendor replies, AI moderation | Out of scope                                     |
| Rating on catalog cards / search                               | Stays on product-detail only                     |
| Block non-purchasers from reviewing                            | Product decision — currently badge-only          |

---

## How the module is structured today

```
product-reviews/          → full hexagonal module
web/.../product-detail    → public reviews section
web/.../admin-dashboard/reviews/ → moderation pages
```

Cross-module: only **ports** from `product-catalog` and `order-checkout` (no adapter imports).

---

## Definition of Done (Epic)

- [x] Submit review + duplicate guard
- [x] Public average / histogram / approved list
- [x] Admin approve / reject
- [x] ArchUnit + tests + WAR builds
- [ ] Optional author self-service UI and notifications

---

_This backlog is a living status document. For the original INVEST story list, see the git history of this file._

```

```
