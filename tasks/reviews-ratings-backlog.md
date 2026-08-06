---

### 3. `reviews-ratings-backlog.md`

```markdown
# Reviews & Ratings Module — Agile Backlog

**Epic goal:** Customers can leave verified star ratings and text reviews; admins can moderate them; product pages display ratings and approved reviews.

**Total stories:** 14
**MVP subset (shippable):** S1–S8 + S12–S13

## Story Map
```

FOUNDATION
S1 Domain model + exceptions
S2 Ports (in + out)
S3 Application service
S4 Persistence adapter + Flyway

PUBLIC CUSTOMER FLOW
S5 Submit review (with verified-purchase)
S6 Product rating summary (average + histogram)
S7 List approved reviews on product page
S8 “Write a review” form + success feedback

ADMIN MODERATION
S9 Pending reviews list (filters + pagination)
S10 Approve review
S11 Reject review (with reason)

CROSS-CUTTING
S12 ArchUnit + RBAC guards
S13 Integration tests (Testcontainers)
S14 (Optional) Hide own review + notification

```

## Stories (INVEST)

### S1 — Domain Model & Exceptions
**Points:** 5 | **Priority:** Must
Create `Review`, `Rating`, `ReviewStatus`, factory methods, business methods (approve/reject), and all domain exceptions.
**DoD:** Unit tests green, zero framework imports, English Javadoc.

### S2 — Ports
**Points:** 3 | **Priority:** Must
All inbound UseCase interfaces + outbound ports (`ReviewRepositoryPort`, `ProductLookupPort`, `OrderVerificationPort`).

### S3 — Application Service
**Points:** 5 | **Priority:** Must
`ReviewApplicationService` implements every UseCase, orchestrates verified-purchase check, uniqueness, status transitions.

### S4 — Persistence + Migration
**Points:** 8 | **Priority:** Must
`ReviewJpaEntity`, mapper, `ReviewRepositoryAdapter`, Flyway script, unique constraint, indexes.
ITs with Testcontainers.

### S5 — Submit Review
**Points:** 5 | **Priority:** Must
Authenticated user can submit rating + optional title/body. Verified-purchase flag calculated. Duplicate prevented.

### S6 — Rating Summary
**Points:** 3 | **Priority:** Must
`GetProductRatingSummaryUseCase` returns average, count, histogram (1★…5★).

### S7 — List Approved Reviews
**Points:** 3 | **Priority:** Must
Paginated list of APPROVED reviews for a product (newest first).

### S8 — Public UI (product-detail)
**Points:** 5 | **Priority:** Must
Stars + average + review cards + “Write a review” form integrated into existing product-detail page. Design tokens only.

### S9 — Admin Pending List
**Points:** 5 | **Priority:** Must
Admin page with filters (status, product, date), pagination, status badges.

### S10 — Approve Review
**Points:** 3 | **Priority:** Must
Admin can approve → status = APPROVED, moderatedAt set, optional notification.

### S11 — Reject Review
**Points:** 3 | **Priority:** Must
Admin can reject with mandatory reason → status = REJECTED.

### S12 — ArchUnit + RBAC
**Points:** 3 | **Priority:** Must
Hexagonal rules + `@RolesAllowed("ADMIN")` on all moderation beans + coverage test style of AdminAccessControlCoverageTest.

### S13 — Full Test Suite
**Points:** 5 | **Priority:** Must
Domain + service unit tests + adapter ITs + end-to-end happy path.

### S14 — Hide Own Review (optional)
**Points:** 3 | **Priority:** Should
Customer can hide their own approved review.

**MVP Definition of Done:** S1–S8 + S12–S13 green, public product page shows ratings, admin can moderate, `mvn clean package -pl web -am` succeeds, no new dependencies.
```
