# Reviews & Ratings Module — Technical Specification (As-Built)

**Status:** Implemented — shipped as **v0.10.0** (2026-08-05).
**Module:** `product-reviews` · **Package:** `com.loja.productreviews`
**Companion docs:** `reviews-ratings-backlog.md` · `reviews-ratings-implementation-sequence.md`
**Release notes:** [docs/releases/v0.10.0.md](../docs/releases/v0.10.0.md)

The original pre-implementation specification is preserved in git history of this file.

---

## 1. Purpose & Scope

Customers leave **1–5★** ratings and optional text reviews on products. Product-detail shows average, histogram and approved reviews. Admins moderate a PENDING queue.

**In scope (delivered):**

- Authenticated submit (title + body, OWASP-sanitized)
- Verified-purchase **badge** (CONFIRMED / SHIPPED / DELIVERED orders)
- Average + star histogram + paginated APPROVED list
- Admin approve / reject (mandatory reason)
- One review per user per product (domain + DB unique)

**Out of scope:**

- Review media, helpfulness votes, vendor replies, AI moderation, incentives
- Author-facing “my reviews” / edit UI
- Moderation email notifications
- Rating chips on catalog cards / search

---

## 2. Architecture

```
com.loja.productreviews/
├── domain/
│   ├── model/       → Review, Rating, RatingAggregate, ReviewStatus
│   ├── port/in/     → Submit, list approved, summary, moderate, hide, …
│   ├── port/out/    → ReviewRepositoryPort, ProductLookupPort, OrderVerificationPort
│   └── exception/
├── application/
│   ├── service/     → ReviewApplicationService
│   └── dto/         → ReviewDTO, RatingSummaryDTO, ReviewListPage, SubmitReviewCommand, …
└── adapter/
    ├── in/web/      → public + moderation beans
    └── out/         → JPA + ProductLookup / OrderVerification adapters
```

**Cross-module rules:**

- Only ports of `product-catalog` and `order-checkout`
- Admin pages under `web/.../admin-dashboard/reviews/` (composition; no domain rules in admin-dashboard)
- Domain and application free of framework imports; ArchUnit enforced

---

## 3. Domain Model

### Review aggregate

| Field                        | Notes                                         |
| ---------------------------- | --------------------------------------------- |
| id, productId, authorId      | System-aligned identifiers                    |
| rating                       | `Rating` VO (1–5)                             |
| title / body                 | Optional; body sanitized in application layer |
| status                       | PENDING → APPROVED \| REJECTED \| HIDDEN      |
| verifiedPurchase             | Boolean flag from order verification          |
| moderatedAt, rejectionReason | Set on moderate / reject                      |

**Business methods:** approve, reject(reason), hide (own review path).

**Exceptions:** `DuplicateReviewException`, `InvalidRatingException`, `ReviewAlreadyModeratedException`, `ReviewNotFoundException`.

### Rating summary

`RatingAggregate` / `RatingSummaryDTO`: average, total count, histogram counts for 1★–5★ (computed in repository query).

---

## 4. Ports & Application

**Inbound (examples):** SubmitReview, ListApprovedByProduct, GetProductRatingSummary, ListPending, Approve, Reject, HideOwn, GetById.

**Outbound:**

- `ReviewRepositoryPort` — save, find, pending list, summary aggregate, uniqueness
- `ProductLookupPort` — product exists / active (thin adapter)
- `OrderVerificationPort` — has purchased product in allowed statuses (thin adapter)

Single **`ReviewApplicationService`** orchestrates validation, uniqueness, verified-purchase flag, status transitions and DTOs.

---

## 5. Persistence

- Table: `tb_product_review`
- Flyway: **`V17__product_reviews.sql`** (+ rollback under `docs/migrations/`)
- Unique: `(author_id, product_id)`
- Indexes supporting product+status and moderation queues
- Entity registered in `web` `persistence.xml`

---

## 6. Web / UI

| Surface | Location                                                                           |
| ------- | ---------------------------------------------------------------------------------- |
| Public  | Reviews block on `product-detail.xhtml` (stars, `barChart` histogram, cards, form) |
| Admin   | `/admin-dashboard/reviews/list.xhtml` + `detail.xhtml`                             |

Design-system tokens only; status badges aligned with existing patterns.

---

## 7. Security

| Action                          | Access                               |
| ------------------------------- | ------------------------------------ |
| Read approved reviews / summary | Anonymous                            |
| Submit review                   | Authenticated customer               |
| Moderate                        | `@RolesAllowed("ADMIN")` + `web.xml` |

---

## 8. Testing & Quality

- Domain unit tests (no mocks)
- Application tests with mocked ports
- Adapter ITs (Testcontainers)
- Bean tests + `ReviewHexagonalArchitectureTest` (8 rules)
- **85** module tests green at v0.10.0
- Lesson #19: persistence.xml, EL getters, Instant formatting, L2 cache after smoke

---

## 9. Non-functional decisions

| Decision                    | Choice                             |
| --------------------------- | ---------------------------------- |
| Verified purchase           | Badge only — does not block submit |
| One review per user×product | Domain + unique constraint         |
| Body HTML                   | OWASP sanitizer (existing pattern) |
| Public page size            | Paginated (default ~10)            |
| Average calculation         | Repository aggregate query         |

---

## 10. Definition of Done

- [x] Public submit + display + summary
- [x] Admin moderation
- [x] Hexagonal boundaries + full test suite
- [x] Release v0.10.0
- [ ] Optional self-service UI and notifications

---

_This document describes the module as implemented. For earlier planning sketches, see the git history of this file._

```

```
