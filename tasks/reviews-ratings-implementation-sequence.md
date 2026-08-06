---

### 4. `reviews-ratings-implementation-sequence.md`

```markdown
# Reviews & Ratings — Implementation Sequence

**Rule:** Execute steps in strict order. Do not start step N+1 until the “Done when” checklist of step N is 100 % satisfied.

## Step 0 — Module Skeleton & Prerequisites

1. Create Maven module `product-reviews` (same structure as `product-catalog`).
2. Add it to root `pom.xml` `<modules>` and dependencyManagement.
3. Create package tree under `com.loja.productreviews`.
4. Confirm next Flyway version number.
5. Confirm design-system tokens for stars/status exist (or request human approval for new ones).

**Done when:** `mvn -pl product-reviews test-compile` succeeds (empty).

## Step 1 — Domain Model & Exceptions

Create `Review`, `Rating`, `ReviewStatus`, all value objects, factory methods, business methods (`approve`, `reject`, `hide`) and the five domain exceptions.
Zero framework imports. Full unit tests (`ReviewTest`, `RatingTest`…).

**Done when:** Domain tests green, ArchUnit domain-free-of-jakarta rule would pass.

## Step 2 — Ports (in + out)

All UseCase interfaces + `ReviewRepositoryPort`, `ProductLookupPort`, `OrderVerificationPort`.
Javadoc with Given/When/Then examples.

**Done when:** Interfaces compile, no implementations yet.

## Step 3 — Application Service

`ReviewApplicationService` implements every UseCase.
Inject ports, enforce uniqueness, call verified-purchase check, transition status, return DTOs or throw domain exceptions.

**Done when:** Service compiles, unit tests with mocked ports green.

## Step 4 — Persistence Adapter + Flyway

- `ReviewJpaEntity` + static mapper
- `ReviewRepositoryAdapter`
- Flyway `Vxx__product_reviews.sql` (table + unique + indexes)
- Adapter ITs with Testcontainers

**Done when:** ITs green, unique constraint enforced.

## Step 5 — Thin Integration Ports

Implement `ProductLookupPort` and `OrderVerificationPort` adapters (call existing product-catalog / order-checkout ports or repositories via the allowed dependency direction).

**Done when:** Both adapters compile and have at least one IT.

## Step 6 — Public Use Cases Wiring + DTOs

Finalize all public DTOs (`ReviewDTO`, `RatingSummaryDTO`, `SubmitReviewCommand`…).
Wire Submit / List / Summary use cases.

**Done when:** Application layer complete and unit-tested.

## Step 7 — Public JSF Bean + product-detail Integration

- `ProductReviewBean` (`@RequestScoped` / `@ViewScoped`)
- Add rating summary + review list + form to existing `product-detail.xhtml`
- Use only design-system tokens
- Success / error FacesMessages

**Done when:** Manual smoke on Open Liberty shows stars and form.

## Step 8 — Admin Moderation UI

- Admin bean(s) with `@RolesAllowed("ADMIN")`
- List page (PENDING filter default) + detail/moderation page
- Approve / Reject actions
- Follow exact visual patterns of existing refund / user management pages

**Done when:** Admin can approve/reject and the change is visible on the public page.

## Step 9 — RBAC & Security Constraints

- `web.xml` security-constraint for `/admin-dashboard/reviews/*` (or equivalent)
- `@RolesAllowed` on every admin bean
- Coverage-style test (scan pages + annotations) if the pattern from admin-dashboard exists

**Done when:** Non-admin receives 403.

## Step 10 — ArchUnit Rules

Copy and adapt `ProductHexagonalArchitectureTest` → `ReviewHexagonalArchitectureTest` (domain free of jakarta, ports are interfaces, adapters implement ports, no cross-adapter imports, etc.).

**Done when:** All ArchUnit rules green.

## Step 11 — Full Test Suite & Polish

- Complete unit + IT coverage
- Sanitize review body (reuse OWASP pattern)
- Pagination, empty states, error messages
- English-only check

**Done when:** `mvn -pl product-reviews,web -am test` green.

## Step 12 — Final Integration & Release Prep

- Add module to CI if needed
- Update README / AGENTS.md with the new module
- Smoke the full flow: submit → moderate → appear on product page
- Tag readiness for v0.9.0 (or next)

**Done when:** Entire epic Definition of Done is satisfied.
```
