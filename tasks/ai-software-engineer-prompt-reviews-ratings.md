# AI Software Engineer Prompt — Reviews & Ratings

**Status:** The Reviews & Ratings module (`product-reviews`) is **implemented** and released as **v0.10.0**.
This prompt is for agents that need to **extend, fix or maintain** it — not for a green-field build.

The original pre-implementation prompt is preserved in git history of this file.

---

## Project context

- Modular monolith: **Jakarta EE 11 + Jakarta Faces** on **Open Liberty**
- Multi-module Maven, **hexagonal architecture**
- Java 21, English-only code and docs

**Sources of truth (read in this order):**

1. `AGENTS.md` — critical rules
2. `docs/lessons.md` — especially lesson #19 (new-module WAR smoke gotchas)
3. `docs/design-system.md` — UI tokens and rule of two
4. `docs/releases/v0.10.0.md` — what was delivered
5. `tasks/reviews-ratings-module-spec.md` · `reviews-ratings-backlog.md` · `reviews-ratings-implementation-sequence.md`
6. Code under `product-reviews/` and related pages in `web/`

---

## What this module is

`product-reviews` is a full hexagonal business module:

```
com.loja.productreviews/
├── domain/
│   ├── model/       → Review (state machine), Rating, RatingAggregate
│   ├── port/in/     → submit, list approved, summary, moderate, …
│   ├── port/out/    → ReviewRepositoryPort, ProductLookupPort, OrderVerificationPort
│   └── exception/   → DuplicateReview, InvalidRating, ReviewAlreadyModerated, …
├── application/
│   ├── service/     → ReviewApplicationService
│   └── dto/         → ReviewDTO, RatingSummaryDTO, ReviewListPage, SubmitReviewCommand
└── adapter/
    ├── in/web/      → public + moderation beans
    └── out/         → JPA adapter + thin ProductLookup / OrderVerification adapters
```

- Public UI: reviews section on `product-detail.xhtml`
- Admin UI: `/admin-dashboard/reviews/` (list + detail)
- DB: Flyway `V17__product_reviews.sql` (unique `(author_id, product_id)`)

---

## Already delivered (do not re-implement)

| Capability                                                | Notes                                                                          |
| --------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Submit 1–5★ review (title + body, OWASP-sanitized)        | One review per user per product                                                |
| Average rating + star histogram + paginated approved list | On product-detail page                                                         |
| Verified-purchase **badge**                               | Based on CONFIRMED/SHIPPED/DELIVERED orders; does **not** block non-purchasers |
| Admin moderation queue                                    | Approve / reject with mandatory reason                                         |
| ArchUnit + 85 module tests                                | `ReviewHexagonalArchitectureTest` green                                        |
| Integration adapters                                      | Only consume `product-catalog` / `order-checkout` **ports**                    |

---

## Explicit debt / out of scope (until requested)

- Review images / media
- Helpfulness voting, vendor replies, AI moderation, incentives
- Author-facing edit / “my reviews” UI (use cases may exist; no screen yet)
- Moderation email notifications (`NotificationPort` deferred)
- Rating summary on catalog cards / search results

---

## Non-negotiable rules

1. Hexagonal boundaries — depend only on other modules’ `domain.port`
2. Zero framework imports in `domain/` and `application/`
3. DTOs live in `application/dto` — **never** nested inside port interfaces (ArchUnit)
4. New JPA entities must be registered in `web/.../persistence.xml`
5. RBAC: moderation beans/pages are `@RolesAllowed("ADMIN")` + `web.xml`
6. No new Maven dependencies without human approval
7. English only; design-system tokens only

---

## How to extend this module safely

1. Change domain rules only inside `product-reviews` (state machine, validation).
2. For product or order data, go through existing outbound ports / adapters.
3. Keep DTOs in `application/dto`.
4. Add or update unit tests + ArchUnit; run ITs with Testcontainers when touching persistence.
5. After any WAR-level change, **browser-smoke** product-detail + moderation pages (lesson #19).
6. Update backlog/status and release notes if the change is a milestone.

**Do not:**

- Import adapters from `product-catalog` or `order-checkout`
- Put review business rules in `admin-dashboard` or `web`
- Block review submission for non-purchasers without an explicit product decision
- Forget `persistence.xml` when adding entities

---

## Useful commands

```bash
# Fast unit + ArchUnit
mvn -pl product-reviews test -Dtest='*Test' -DfailIfNoTests=false

# Integration tests (Testcontainers)
mvn -pl product-reviews test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false

# Full WAR
mvn clean package -pl web -am

# Run
./scripts/run-liberty.sh
```

Smoke path (see also `docs/releases/v0.10.0.md`):

1. Login as customer/admin
2. Product detail → submit review
3. Admin → `/admin-dashboard/reviews/` → approve
4. Reload product detail → review visible + histogram updated

---

## Known gotchas (lesson #19)

- Register every new JPA entity in `persistence.xml` (`exclude-unlisted-classes=true`)
- EL needs JavaBean getters (`isHasReviews`, not `hasReviews`)
- Facelets: numeric entities only (`&#171;`, not `&laquo;`)
- Format `Instant` in the bean; `f:convertDateTime` does not support it
- Restart Open Liberty after out-of-band data fixes (EclipseLink L2 cache)

---

## When stuck

Stop and ask the human if:

- A new third-party dependency is required
- Behaviour should change for non-purchasers (block vs badge-only)
- Cross-module contract changes are needed beyond existing ports

Do **not** push to the remote unless the human explicitly asks.

---

_Original green-field implementation prompt remains available in the git history of this file._

```

```
