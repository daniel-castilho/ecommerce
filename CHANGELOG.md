# Changelog

All notable changes to this project are documented in the individual release notes under [`docs/releases/`](docs/releases/).

This file provides a high-level index of every tagged release.

---

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
