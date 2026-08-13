# Changelog

All notable changes to this project are documented in the individual release notes under [`docs/releases/`](docs/releases/).

This file provides a high-level index of every tagged release.

---

## [v0.19.3](docs/releases/v0.19.3.md) — 2026-08-13

**Admin coupon list regression fix + documented release smoke**
The admin coupon list 500'd (`Cannot format given Object as a Date`) for any coupon with a
validity window because `<f:convertDateTime>` cannot format `java.time.Instant`; the window now
renders via `CouponManagementBean.formatUtc(...)` with a regression test. `docs/testing-playbook.md`
gains a "Release regression smoke" section — required browser steps + read-only SQL proofs —
so UI regressions like this are caught before every tag.

## [v0.19.2](docs/releases/v0.19.2.md) — 2026-08-13

**Order notification emails are multipart/alternative**
The outbox snapshots an inline-styled HTML variant (`body_html`, V30) next to the plain-text body
at claim time and the poller sends `multipart/alternative` (`text/plain` + `text/html`) from those
stored snapshots. One shared HTML layout with design-token colors, HTML-escaped user/catalog
strings; resend re-sends the same snapshots, pre-V30 rows stay text-only. No new dependency,
checkout still never blocks on SMTP.

## [v0.19.1](docs/releases/v0.19.1.md) — 2026-08-13

**Admin PDF reports embed vector charts**
Revenue, Product Performance and Customer Insights PDF exports now include a bar/line chart
mirroring the on-screen series, drawn with pure OpenPDF vector primitives
(`PdfChartDrawer`) using the design-token colors. Series reuse the UI chart DTOs — no
re-query, no new dependency; CSV and the data tables are unchanged.

## [v0.19.0](docs/releases/v0.19.0.md) — 2026-08-11

**Coupon eligibility scope + per-user redemption cap**
Coupons can now target specific products or product categories (`CouponScope`: ALL /
PRODUCT / CATEGORY) and cap how many times a single user may redeem them. The checkout
quotes against the eligible cart lines only — including the live JSF preview — and
redemption records a per-user ledger row (`tb_coupon_redemption`, V29) so the cap holds
across orders. Admin create form exposes scope, CSV product/category ids and the per-user
cap; the coupon list shows the scope. Existing whole-order coupons keep `ALL` scope.

## [v0.18.4](docs/releases/v0.18.4.md) — 2026-08-11

**Hardening: guest-cart edge cases + atomic coupon redemption**
Checkout rejects cart lines whose product is missing or no longer ACTIVE (clean
`CartProductNotAvailableException` instead of ordering stale inventory). The guest-cart
merge can no longer fail a login: optimistic-lock conflicts retry on fresh transactions
and any other failure is logged and swallowed. Coupon invariants are tightened: `create()`
rejects a reversed validity window, `applyCoupon()` refuses a second coupon, and redemption
reads under a pessimistic write lock so concurrent checkouts can never over-book
`used_count`. Covered by new unit tests, a guest→merge→coupon→checkout IT, and a concurrent
redemption IT.

## [v0.18.3](docs/releases/v0.18.3.md) — 2026-08-11

**Confirm modal no-JSF fallback submits via `requestSubmit`; logout form fix**
The admin "Resend" confirm button silently did nothing on pages without `faces.js`: the
no-JSF fallback called `el.click()` on the guarded button, whose `return false` onclick
cancelled the submit. It now submits the real form via `form.requestSubmit(el)` (button
name/value included, so the JSF action resolves), verified end-to-end in a real browser
(row resets to PENDING). Profile logout is also wrapped in a proper `<h:form>`.

## [v0.18.2](docs/releases/v0.18.2.md) — 2026-08-11

**QA fixes: admin resend + poller shutdown hygiene**
Closes the two defects the v0.18.1 smoke surfaced. The admin "Resend" action on the
notification delivery log now actually re-queues the row (stable button id + `this.id`
target), and `confirm-modal.xhtml` was rewritten from a JSF composite to a Facelets tag
matching its `loja.taglib.xml` registration (composite markup in a tag slot 500'd admin
pages; `&&` now escaped). The outbox poller also cancels its `ScheduledFuture` on
`@Destroyed(ApplicationScoped.class)`, so a hot redeploy no longer leaks a zombie
5-second poller failing against the destroyed Weld context.

## [v0.18.1](docs/releases/v0.18.1.md) — 2026-08-10

**Notification outbox hardening (backoff + EXHAUSTED, admin delivery log)**
Follow-up to the order-notifications epic. FAILED deliveries are gated behind an exponential-ish
backoff (`next_attempt_at`, V28) and the final of `MAX_ATTEMPTS = 3` failures escalates to a new
EXHAUSTED terminal status the poller never touches again. A new admin "Notification Deliveries"
page lists the outbox with status filter and a manual Re-queue action (`resend`) for stuck rows.
Also fixes a latent mail-session defect: the configured session was never injected
(`CWNEN1004E` — `java:app/env/...` jndiName), and a null-session fallback masked it; the canonical
`jndiName="mail/Session"` + `@Resource(lookup=...)` pattern now delivers a real session and the
FROM address (`mail.from` in server.xml).

## [v0.18.0](docs/releases/v0.18.0.md) — 2026-08-10

**Order notifications epic (async transactional outbox)**
Closes the order-notifications epic. Checkout never touches SMTP: an event is claimed as a
PENDING row on the delivery log (V26) in the business transaction, with a rendered email snapshot
(recipient/subject/body, V27); a 5 s poller (`NotificationOutboxProcessor` +
`NotificationOutboxDispatcher`, no EJB) dispatches due rows and records SENT/FAILED with retries
up to 3 attempts. Best-effort + at-least-once: unique idempotency key, preference-aware, and SMTP
outages never block the order transaction.

## [v0.17.1](docs/releases/v0.17.1.md) — 2026-08-10

**Catalog FTS native-query parameter fix (EclipseLink)**
Patch for a runtime-only defect the v0.17.0 smoke exposed: catalog text search returned 500 because
the FTS native query used named parameters, which EclipseLink (the WAR's JPA provider) does not bind
in native SQL — it sent the raw `:param` literals to PostgreSQL. The fix moves the FTS native
queries to positional parameters (`?N`), which bind on both EclipseLink and Hibernate; the
Testcontainers ITs (Hibernate) were green but never covered the runtime provider. Behavior unchanged.

## [v0.17.0](docs/releases/v0.17.0.md) — 2026-08-10

**Guest cart + merge on login (S12); catalog Postgres FTS ranking**
Closes the persistent-cart epic's last story and delivers the catalog full-text search ranking
epic. Guests can add to a durable, session-scoped cart and the flow ends with a checkout that
still requires an account; on login `GuestCartMergeObserver` folds the guest cart into the user
cart (`Cart.merge` sums quantities). Catalog text search now ranks by `ts_rank` over
name/SKU/short description with a GIN expression index (V25), a prefix `tsquery` + ILIKE fallback,
and `RELEVANCE` as the default catalog sort.

## [v0.16.1](docs/releases/v0.16.1.md) — 2026-08-09

**Catalog-card "Add to cart" (S11)**
Closes the persistent-cart epic's last optional story. Every catalog card gains an **Add to
cart** button (logged-in users, active/in-stock products); guests see a **Log in to buy** link.
Reuses `CartBean.addProduct` and the global "Product added to your cart." message — UI only, no
domain or application changes.

## [v0.16.0](docs/releases/v0.16.0.md) — 2026-08-09

**Persistent cart (S1–S10)**
Replaces view-scoped checkout lines with a durable, per-user cart in Postgres. Customers add
products on the product detail, edit quantities on a new `/web/order-checkout/cart.xhtml`, and
check out — the order is built from the persisted cart and the cart is cleared only after a
confirmed order (a server restart keeps items). Optimistic locking on the cart, live catalog
prices (no frozen unit price), migration V24. Also rolls in previously untagged work: **coupons**
(create/list + checkout discount, V22/V23) and **richer refund list filters** (status, customer,
date range, sort).

## [v0.15.0](docs/releases/v0.15.0.md) — 2026-08-08

**Audit log: resolved admin name & entity columns**
Closes the last deferred acceptance criterion of the S24 audit log viewer. The actor column now
shows the **resolved admin full name** (via `FindUserUseCase`) instead of a raw id, and the
**entity type/ID** (USER, PRODUCT, REFUND, ADDRESS) appear as separate columns from new
`entity_type`/`entity_id` columns (migration V21). Also fixes `PRODUCT_ARCHIVED`/`REFUND_*`
events so the actor is recorded in `actor_id` (previously overloaded the subject column), letting
the Admin column resolve for cross-entity actions.

## [v0.14.1](docs/releases/v0.14.1.md) — 2026-08-07

**Wishlist catalog-card heart icon (S10)**
Closes the wishlist epic (S1–S10). Every catalog card gets a **♥/♡ heart** toggle that adds or
removes the product from the wishlist (guests see a heart linking to login), reusing the same
use cases. Adds `WishlistBean.toggleFor` / `inWishlistFor`, heart styles from existing tokens,
6 new bean tests, and lesson #27 (EL method invocation needs the literal method name).

## [v0.14.0](docs/releases/v0.14.0.md) — 2026-08-07

**Wishlist public UI**
Completes the Wishlist epic (S1–S9). Customers can now **add/remove products from a wishlist** on
the product detail page (toggle button; guests see *Log in to save*) and manage it at
`/web/wishlist/wishlist.xhtml` (list with image, name, price and added date, per-item remove,
empty state). Also fixes `WishlistBean` FacesMessages so the text renders (summary-only
`h:messages` convention) and adds the wishlist page styles from existing design tokens only.

## [v0.13.0](docs/releases/v0.13.0.md) — 2026-08-07

**Order timeline, blocked-checkout guard, cost price & audit filters**
Four back-office improvements closing long-standing debt: suspended/blocked accounts can no longer
check out (`AccountSuspendedException`); the admin order detail shows a **status timeline** (V18
`order_status_history`, seeded on placement, appended on every transition) plus the **payment
snapshot**; products gain an admin-only **cost price** (V19) with **gross profit margin** columns on
the admin product list and the product performance report; and the **audit log** gains actor/event
type/details keyword/date-range **filters** with a full-text **tooltip** on long details (S24 debt
closed). Includes QA fixes surfaced only by browser smoke (MyFaces `Instant` conversion, invalid
XML named entities on five pages, UTC date filters). The wishlist module (V20) landed as a separate
commit in this window; its customer-facing UI is deferred.

## [v0.12.0](docs/releases/v0.12.0.md) — 2026-08-06

**Report Export (S23)**
Closes the admin-dashboard reporting lane. All three reports (revenue, product performance, customer insights) can now be exported to **CSV** (UTF-8 BOM, Excel-friendly) or **PDF** (design-token-styled tables) via a single `ReportExportPort` adapter using the human-approved Apache Commons CSV 1.11.0 and OpenPDF 1.3.43. Downloads stream to the servlet response with `Content-Disposition` filenames embedding the date range (or category slug) and stay under the `/admin-dashboard/*` RBAC constraint.

## [v0.11.0](docs/releases/v0.11.0.md) — 2026-08-05

**Customer Insights Report (S22)**
Closes the admin-dashboard reporting lane. Adds the Customer Insights report with KPIs (Total Customers, New Customers, Repeat Rate, Average LTV, Churn Rate) and a “New Customers by Date” line chart. Metrics are composed from `order-checkout` and `user-account`. Also fixes the Facelets `rendered` attribute bug on plain HTML elements.

## [v0.10.0](docs/releases/v0.10.0.md) — 2026-08-05

**Reviews & Ratings module**
Full hexagonal `product-reviews` module: customers can submit 1–5★ reviews, view average rating + star histogram + paginated approved reviews, and see a verified-purchase badge. Admins moderate reviews at `/admin-dashboard/reviews/`. Includes Flyway migration `V17__product_reviews.sql` and dedicated ArchUnit rules.

## [v0.9.0](docs/releases/v0.9.0.md) — 2026-08-05

**Product Performance Report (S21)**
Second story of the reporting lane. Adds top/bottom sellers by units and revenue, units-by-category bar chart, and extracts the shared `barChart` component. Includes several important QA fixes (Facelets composites, enum converters, design-tokens import, and `rendered` guards).

## [v0.8.0](docs/releases/v0.8.0.md) — 2026-08-05

**Revenue Report (S20)**
First story of the admin reporting lane. Date-range revenue report with Daily/Weekly/Monthly grouping, KPI cards, CSS bar chart and payment-method breakdown.

## [v0.7.0](docs/releases/v0.7.0.md) — 2026-08-03

**Monitoring & Security lane (S25–S27)**
Admin user management (list, role assignment, block/unblock), automated RBAC coverage guard (`AdminAccessControlCoverageTest`), and ArchUnit boundary tests for `admin-dashboard`.

## [v0.6.0](docs/releases/v0.6.0.md) — 2026-08-03

**Admin back-office management**
Orders, products, customers and refunds management screens, plus the refund request workflow in `order-checkout` (`V16`). Includes dashboard KPIs, audit log viewer and confirm-modal improvements.

## [v0.5.0](docs/releases/v0.5.0.md) — 2026-08-02

**Real dashboard metrics + login fix**
Live KPIs on the admin dashboard. Programmatic login now correctly uses `SecurityContext.authenticate(...)` (Open Liberty / JASPI compatibility).

## [v0.4.0](docs/releases/v0.4.0.md) — 2026-08-02

**RBAC bug-fix + status-badge alignment**
Login now verifies the password only once per attempt. Status-badge CSS fully aligned with all status enums and protected by `StatusBadgeCssCoverageTest`.

## [v0.3.0](docs/releases/v0.3.0.md) — 2026-08-01

**Real Jakarta Security RBAC**
Introduces `UserIdentityStore` + `LoginAuthenticationMechanism`, security constraints in `web.xml`, and `SecurityContext`-backed user session.

## [v0.2.0](docs/releases/v0.2.0.md) — 2026-08-01

**Order & Checkout epic complete**
Full order state machine, 4-step checkout wizard, inventory reservation, optimistic locking, order history, cancel/refund flows and ArchUnit guards.

## [v0.1.0](docs/releases/v0.1.0.md) — 2026-08-01

**Baseline**
Initial modular monolith with `user-account`, `product-catalog` storefront and basic order checkout MVP.
