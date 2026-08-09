# Admin Dashboard — Backlog Status

**Companion documents:**
`admin-dashboard-module-spec.md` · `admin-dashboard-implementation-sequence.md`

**Epic goal:** Provide administrators with a functional back-office to manage orders, products, customers, refunds and view operational reports, while respecting hexagonal boundaries (composition only — no business rules inside `admin-dashboard`).

---

## Current Status Summary (as of v0.13.0)

| Lane                      | Status         | Notes                                                |
| ------------------------- | -------------- | ---------------------------------------------------- |
| Foundation / Metrics      | ✅ Done        | Real KPIs composed from other modules                |
| Dashboard Home            | ✅ Done        | Live counts + basic overview                         |
| Order Management          | ✅ Done        | List, detail + status timeline + payments, status update |
| Product Management        | ✅ Done        | Create / edit / archive / reactivate; cost price + margin |
| Customer Management       | ✅ Done        | List, detail, block/unblock, roles                   |
| Refund Management         | ✅ Done        | List, approve, reject (workflow in order-checkout)   |
| Reporting (S20–S22)       | ✅ Done        | Revenue, Product Performance (+ margin), Customer Insights |
| Monitoring & Security     | ✅ Done        | Audit log + filters/tooltip, admin user mgmt, RBAC guard, ArchUnit |
| Report Export (S23)       | ✅ Done        | CSV/PDF export (v0.12.0, Apache Commons CSV + OpenPDF) |
| Advanced charts / caching | ⚠️ Right-sized | Simplified CSS/SVG charts used; Guava cache deferred |

---

## Implemented Stories

### Foundation & Dashboard

- **S1–S3** — Query ports/DTOs and aggregation queries delivered via composition of existing module ports (no dedicated “admin query adapters” layer was needed).
- **S4** — Dashboard home with real KPIs (`totalUsers`, `totalProducts`, `totalOrders`, etc.) — released in **v0.5.0 / v0.6.0**.

### Order Management (v0.6.0)

- **S6** Order list with filters & pagination
- **S7** Order detail
- **S8** Update order status

### Product Management (v0.6.0)

- **S10–S13** Admin product list, create, edit, archive/reactivate (with audit trail). Reuses `product-catalog` use cases.

### Customer Management (v0.6.0 + v0.7.0)

- **S14–S16** Customer list/detail, block/unblock, role assignment.

### Refund Management (v0.6.0)

- **S17–S19** Refund request list + approve/reject.
  Core workflow lives in `order-checkout` (`RefundRequest` state machine + `V16` migration); admin-dashboard only provides the UI.

### Reporting Lane

- **S20** Revenue report (date range, Daily/Weekly/Monthly, KPIs, bar chart, payment-method breakdown) — **v0.8.0**
- **S21** Product performance report (top/bottom sellers, units-by-category) — **v0.9.0**
- **S22** Customer insights report (Total/New/Repeat/LTV/Churn + line chart) — **v0.11.0**

### Monitoring & Security (v0.7.0 + v0.13.0)

- **S24** Audit log viewer — **v0.7.0**; filters (actor, event type, details keyword, date range)
  and full-text tooltip on truncated details — **v0.13.0**. Remaining acceptance criteria now
  closed: resolved admin-name actor column (`FindUserUseCase` → full name) and entity type/ID
  as separate columns (`entity_type`/`entity_id`, V21 migration).
- **S25** Admin user management
- **S26** RBAC enforcement (`@RolesAllowed("ADMIN")` + `web.xml` constraints + `AdminAccessControlCoverageTest`)
- **S27** ArchUnit boundary tests for `admin-dashboard`

### Order timeline (S7 / S18–S19 debt, v0.13.0)

- Admin order detail now shows the **status timeline** (`order_status_history`, V18 — seeded
  "Order placed", appended on every transition) and a **payment snapshot**, composed by
  `GetOrderDetailsService`.

### Product cost price & margin (S10/S21 debt, v0.13.0)

- `cost_price` on `tb_product` (V19, nullable, admin-only) with **gross profit margin** columns on
  the admin product list and the product performance report; sales stats exposed via
  `ProductSalesStatsUseCase`.

### Related (outside original 27 stories)

- Review moderation UI (`/admin-dashboard/reviews/`) delivered together with the `product-reviews` module (**v0.10.0**).

---

## Still Pending

### Intentionally right-sized / deferred

- Guava (or other) metrics cache with 5-minute TTL
- Conversion rate, cart abandonment, tax/net revenue, revenue-by-category (domain does not yet hold the required fields)
- Advanced charting libraries (PrimeFaces Charts, etc.)
- Full “27-story” original vision of heavy query adapters inside admin-dashboard (architecture evolved to pure composition)

---

## How the module is actually structured today

- `admin-dashboard` contains **only** composition services and JSF beans.
- All business data and calculations live in the owning modules (`order-checkout`, `user-account`, `product-catalog`, `product-reviews`) and are exposed via ports/use cases.
- Security is enforced by Jakarta Security + `AdminAccessControlCoverageTest`.
- UI follows the design-system tokens and shared components (`barChart`, status badges, etc.).

---

## Definition of Done (Epic)

- [x] Dashboard with real KPIs
- [x] Order / Product / Customer / Refund management
- [x] Three operational reports (S20–S22)
- [x] Audit log + Admin user management
- [x] Full RBAC + ArchUnit guards
- [x] Report export (S23) — CSV/PDF (v0.12.0)
- [x] Order status timeline + payments, cost price & margin, audit log filters (v0.13.0)

---

_This backlog is now a living status document rather than a full INVEST story catalogue. For historical detail of the original 27 stories, see git history of this file._

```

```
