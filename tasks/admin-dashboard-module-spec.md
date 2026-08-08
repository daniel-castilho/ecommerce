# Admin Dashboard Module — Technical Specification (As-Built)

**Status:** Living document reflecting the implemented module (through S23 report export).
**Companion docs:** `admin-dashboard-backlog.md` · `admin-dashboard-implementation-sequence.md`

The original long-form pre-implementation specification is preserved in git history of this file.

---

## 1. Purpose & Architectural Role

`admin-dashboard` is a **composition module**. It does **not** own business rules, aggregates or JPA entities for orders, products, customers or refunds.

It:

- Composes use cases / ports from other modules (`order-checkout`, `product-catalog`, `user-account`, `product-reviews`)
- Provides ADMIN-only JSF screens and thin managed beans
- Owns only presentation DTOs, report export models and the report-export adapter

**Hard rules (enforced by ArchUnit):**

- Zero business rules in this module
- Depend only on other modules’ `domain.port` interfaces
- Every admin page/bean is protected by `@RolesAllowed("ADMIN")` + `web.xml` security-constraints (`AdminAccessControlCoverageTest`)

---

## 2. Package Layout (actual)

```
com.loja.admindashboard/
├── domain/
│   ├── model/          → PdfDocument, PdfSection, PdfKeyValue, CsvTable (export payloads)
│   ├── exception/      → ReportGenerationException (and similar)
│   └── port/
│       └── out/        → ReportExportPort
├── application/
│   └── dto/            → DashboardSummaryDTO, ChartBar, ChartLine, OrderDetailsDTO, …
└── adapter/
    ├── in/web/         → Thin @Named JSF beans (@RolesAllowed("ADMIN"))
    └── out/reporting/  → ReportGeneratorAdapter (OpenPDF + Commons CSV)
```

Pages live under `web/src/main/webapp/admin-dashboard/`.

---

## 3. What Was Implemented

### Dashboard home

- Live KPIs composed from other modules (`DashboardBean` + `DashboardSummaryDTO`)
- Released progressively in **v0.5.0 / v0.6.0**

### Order management

- List (filters + pagination), detail, status update
- Beans: `OrderManagementBean`, `AdminOrderDetailBean`
- Business logic stays in `order-checkout`

### Product management

- List, create, edit, archive / reactivate
- Beans: `ProductManagementBean`, `ProductCreateBean`, `ProductEditBean`
- Reuses `product-catalog` use cases

### Customer management

- List, detail, block/unblock, role assignment
- Beans: `AdminCustomerDetailBean` (+ related)
- Reuses `user-account` ports

### Refund management

- List + detail with approve/reject
- Beans: `RefundManagementBean`, `RefundDetailBean`
- Workflow (`RefundRequest` state machine, payment mock, `V16`) lives in `order-checkout`

### Reporting lane (S20–S22)

| Story | Page                      | What it does                                                                     |
| ----- | ------------------------- | -------------------------------------------------------------------------------- |
| S20   | `reports/revenue.xhtml`   | Date range, Daily/Weekly/Monthly grouping, KPIs, bar chart, payment-method table |
| S21   | `reports/products.xhtml`  | Top/bottom sellers, units-by-category (shared `barChart` tag)                    |
| S22   | `reports/customers.xhtml` | Total/New/Repeat/LTV/Churn + line chart                                          |

Metrics are calculated in the owning modules; admin-dashboard only composes and renders.

### Monitoring & security (S24–S27)

- Audit log viewer (`AuditLogBean`)
- Admin user management
- RBAC coverage test + ArchUnit boundary tests

### Review moderation UI

- `/admin-dashboard/reviews/` (list + detail approve/reject)
- Delivered with the `product-reviews` module (**v0.10.0**); not owned by admin-dashboard domain

### Report export (S23)

- `ReportExportPort` + domain models (`PdfDocument`, `CsvTable`, …)
- `ReportGeneratorAdapter` using **OpenPDF** (PDF) and **Apache Commons CSV**
- Colors mirror design-system tokens
- Charts are **not** embedded as images; series are exported as data tables (right-sized)

---

## 4. How It Differs From the Original Spec

| Original aspiration                                                 | What was actually built                             |
| ------------------------------------------------------------------- | --------------------------------------------------- |
| Heavy query adapters + own DTOs inside admin-dashboard              | Composition of existing module ports/use cases      |
| Rich domain metrics (conversion rate, cart abandonment, tax/net, …) | Only metrics the current domain can support         |
| Guava 5-min metrics cache                                           | No dedicated cache layer                            |
| PrimeFaces / Chart.js                                               | CSS / SVG charts + shared Facelet tags (`barChart`) |
| Freemarker PDF templates                                            | Programmatic OpenPDF tables                         |
| Dedicated admin JPA entities                                        | None — no persistence unit ownership                |

These deviations are intentional and keep hexagonal boundaries clean.

---

## 5. UI & Design System

- All pages under `/admin-dashboard/**`
- Use only design-system tokens (`design-tokens.css`) — no hard-coded colors/spacing
- Shared components: status badges, confirm modal, `barChart` tag
- Conditional rendering: always use `<h:panelGroup>` / `<ui:fragment>`, never `rendered` on plain HTML (lesson #20)

---

## 6. Security

1. `@RolesAllowed("ADMIN")` on every admin bean
2. `web.xml` security-constraints for admin URL patterns
3. `AdminAccessControlCoverageTest` fails the build on drift in either layer

---

## 7. Testing Expectations

- Unit tests for beans and any composition services (Mockito + AssertJ)
- ArchUnit: `AdminDashboardHexagonalArchitectureTest`
- Cross-cutting guards live in the `web` module
- After changing a dependency module, run consumer tests with `-am`

---

## 8. Still Open / Explicit Debt

- Embed chart images in PDF exports (currently data tables only)
- Additional report metrics that require domain growth (tax, cost price, margin, category revenue on order lines, …)
- Notification of customers on status/refund changes (depends on a future notification module)

---

## 9. Definition of Done (module)

- [x] Dashboard with real KPIs
- [x] Order / Product / Customer / Refund management screens
- [x] Revenue, Product Performance and Customer Insights reports
- [x] Audit log + admin user management
- [x] Full RBAC + ArchUnit
- [x] CSV/PDF export via `ReportExportPort`

---

_This document describes the module as implemented. For the original pre-build design (ports list, story-level DTOs, PrimeFaces charts, etc.), see the git history of this file._

```

```
