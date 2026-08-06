---

### 2. `reviews-ratings-module-spec.md`

```markdown
# Reviews & Ratings Module — Technical Specification

**Status:** Implemented — shipped in `v0.10.0` (2026-08-05)
**Target module name:** `product-reviews`
**Root package:** `com.loja.productreviews`

## 1. Purpose & Scope

Allow customers to leave star ratings (1–5) and optional text reviews on products.
Support verified-purchase badge, public display on product-detail page, and an admin moderation queue.

**In scope (MVP):**
- Submit review (authenticated user)
- Optional verified-purchase check (via order-checkout)
- Average rating + star histogram on product page
- List of approved reviews (paginated)
- Admin moderation (list PENDING, approve, reject with reason)
- Soft-delete / hide of own review by customer (optional)

**Out of scope (later):**
- Review images / media
- Helpfulness voting
- Reply-to-review by vendor
- AI moderation
- Review incentives / points

## 2. Architecture

Classic hexagonal, identical to `product-catalog` and `user-account`.

```
product-reviews/
├── domain/
│   ├── model/          Review, ReviewStatus, Rating (Value Object)
│   ├── port/in/        UseCases
│   ├── port/out/       ReviewRepositoryPort, ProductLookupPort, OrderVerificationPort
│   └── exception/
├── application/
│   ├── service/        ReviewApplicationService
│   └── dto/
└── adapter/
    ├── in/web/         JSF beans (public + admin)
    └── out/persistence/ ReviewJpaEntity + ReviewRepositoryAdapter
```

**Cross-module rules:**
- Depends only on ports of `product-catalog` (product existence) and `order-checkout` (verified purchase)
- `admin-dashboard` may compose the moderation UseCases (never the other way around)
- `web` module hosts the final `.xhtml` pages and aggregates the WAR

## 3. Domain Model

### 3.1 Aggregate Root: `Review`

```java
public final class Review {
    private final ReviewId id;
    private final ProductId productId;
    private final UserId authorId;
    private final Rating rating;          // 1–5
    private final String title;           // optional, max 120
    private final String body;            // optional, max 2000, sanitized
    private ReviewStatus status;          // PENDING → APPROVED | REJECTED
    private final boolean verifiedPurchase;
    private final Instant createdAt;
    private Instant moderatedAt;
    private String rejectionReason;       // only when REJECTED
    // ... factory methods, business methods (approve, reject, hide)
}
```

### 3.2 Value Objects
- `Rating` (int 1–5, validated)
- `ReviewId`, `ProductId`, `UserId` (UUIDs or Longs consistent with the rest of the system)
- `ReviewStatus` enum: `PENDING`, `APPROVED`, `REJECTED`, `HIDDEN`

### 3.3 Domain Exceptions
- `ReviewNotFoundException`
- `DuplicateReviewException` (one review per user per product)
- `InvalidRatingException`
- `ReviewAlreadyModeratedException`
- `ProductNotFoundException` (re-exported or mapped)

## 4. Ports

### 4.1 Inbound (Use Cases)
- `SubmitReviewUseCase`
- `ListApprovedReviewsByProductUseCase`
- `GetProductRatingSummaryUseCase` (average + histogram)
- `ListPendingReviewsUseCase` (admin)
- `ApproveReviewUseCase`
- `RejectReviewUseCase`
- `HideOwnReviewUseCase` (customer)

### 4.2 Outbound
- `ReviewRepositoryPort` (save, findById, findByProduct, findPending, existsByUserAndProduct, …)
- `ProductLookupPort` (exists / isActive) — thin adapter over product-catalog
- `OrderVerificationPort` (hasUserPurchasedProduct) — thin adapter over order-checkout
- `NotificationPort` (optional – notify author on moderation)

## 5. Application Layer
Single `ReviewApplicationService` implementing all UseCases.
Orchestrates validation, verified-purchase check, persistence and events (if any).

## 6. Persistence
- Table `tb_product_review`
- Columns: id, product_id, author_id, rating, title, body, status, verified_purchase, created_at, moderated_at, rejection_reason, version
- Indexes: (product_id, status), (author_id, product_id) unique, (status, created_at)
- Flyway migration `V17__product_reviews.sql` (or next free number)

## 7. Web / UI
- Public: section inside existing `product-detail.xhtml` (average stars + list + “Write a review” form)
- Admin: `/admin-dashboard/reviews/` list + detail/moderation page (follows existing refund/user patterns)
- Design tokens only (stars, status badges, cards)

## 8. Security
- Submit: authenticated user (`@RolesAllowed("CUSTOMER")` or any logged-in)
- Moderation: `@RolesAllowed("ADMIN")`
- Public read: anonymous

## 9. Testing
- Domain unit tests (no mocks)
- Application service unit tests (mocked ports)
- Adapter ITs with Testcontainers
- ArchUnit hexagonal rules (copy style from ProductHexagonalArchitectureTest)
- Coverage of verified-purchase path and moderation state machine

## 10. Non-functional
- One review per user per product (enforced in domain + unique constraint)
- Body sanitized with existing OWASP HTML sanitizer pattern
- Pagination on public list (default 10)
- Average rating calculated in repository (or cached later)
```
