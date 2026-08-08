# Admin Dashboard — Implementation Sequence (As-Built)

**Companion docs:** `admin-dashboard-module-spec.md` · `admin-dashboard-backlog.md`

This document records the **actual delivery order** of the admin-dashboard work, not the original pre-build plan.
The original long step-by-step sequence is preserved in git history of this file.

---

## Guiding principle used throughout

`admin-dashboard` is a **composition module**:

- No own business rules
- No own JPA entities for orders/products/customers/refunds
- Depends only on other modules’ ports / use cases
- Thin JSF beans + presentation DTOs + report-export adapter

Work was delivered in vertical slices (screen + composition + tests), not as a big-bang foundation of 27 DTOs and query adapters.

---

## Actual delivery sequence

### Phase 1 — Foundation & access control

**Releases:** v0.3.0 → v0.5.0

1. Real Jakarta Security RBAC (`UserIdentityStore`, `LoginAuthenticationMechanism`, `web.xml` constraints)
2. Login bug-fix (single password verification, `SecurityContext.authenticate`)
3. Live dashboard KPIs composed from existing modules (S2 / S4)

**Outcome:** Admins can log in and see real metrics on `dashboard.xhtml`.

---

### Phase 2 — Core back-office management

**Release:** v0.6.0

Vertical slices delivered together:

| Area       | What landed                                |
| ---------- | ------------------------------------------ |
| Orders     | List, detail, status update                |
| Products   | List, create, edit, archive/reactivate     |
| Customers  | List, detail, block/unblock                |
| Refunds    | List, detail, approve/reject UI            |
| Audit      | Audit log viewer                           |
| Supporting | Confirm-modal fix, design-system alignment |

Refund **workflow** (state machine, payment mock, Flyway `V16`) lives in `order-checkout`.
Product archive/reactivate lives in `product-catalog`.
Admin-dashboard only composes and renders.

---

### Phase 3 — Monitoring & security lane

**Release:** v0.7.0 (S25–S27)

1. Admin user management (list, roles, block/unblock)
2. `AdminAccessControlCoverageTest` (source-scan of pages + beans)
3. `AdminDashboardHexagonalArchitectureTest` (ArchUnit boundaries)

**Outcome:** RBAC drift is caught in CI; module boundaries are guarded.

---

### Phase 4 — Reporting lane

**Releases:** v0.8.0 → v0.11.0

| Step | Story | Deliverable                                                                      |
| ---- | ----- | -------------------------------------------------------------------------------- |
| 4.1  | S20   | Revenue report (date range, grouping, KPIs, CSS bar chart, payment-method table) |
| 4.2  | S21   | Product performance report + extraction of shared `barChart` Facelet tag         |
| 4.3  | S22   | Customer insights report (KPIs + line chart)                                     |

Important QA fixes that accompanied this lane:

- Facelets `rendered` on plain HTML is a no-op → use `<h:panelGroup>` / `<ui:fragment>`
- Enum converter empty-string trap on “All” filters
- Design-tokens CSS import via Faces resource URL
- MyFaces composite-inside-`ui:fragment` NPE → Facelet tag files instead of `cc:` composites

Metrics calculation stays in the owning modules (`order-checkout`, `user-account`); admin-dashboard only composes.

---

### Phase 5 — Report export (S23)

**In progress / largely implemented in tree**

1. Domain models: `PdfDocument`, `PdfSection`, `PdfKeyValue`, `CsvTable`
2. Outbound port: `ReportExportPort`
3. Adapter: `ReportGeneratorAdapter` (OpenPDF + Apache Commons CSV)
4. Colors mirrored from design-system tokens
5. Charts exported as data tables (not embedded images) — intentional right-sizing

**Note:** OpenPDF uses `java.awt.Color` (lesson #21). Keep the library dependency inside the adapter only.

---

### Related work outside the original sequence

- **Review moderation UI** (`/admin-dashboard/reviews/`) shipped with `product-reviews` (v0.10.0). Admin-dashboard hosts the pages; domain lives in `product-reviews`.

---

## What was deliberately _not_ done (vs original plan)

| Original plan                                         | Reality                                         |
| ----------------------------------------------------- | ----------------------------------------------- |
| New `admin-domain` module with 21 use-case interfaces | Composition beans calling existing module ports |
| Dedicated query adapters + Guava cache                | No Guava; queries live in owning modules        |
| 27+ admin-specific DTOs up front                      | Only the DTOs actually needed by screens        |
| PrimeFaces / Chart.js                                 | CSS/SVG + shared Facelet tags                   |
| Flying Saucer + Freemarker                            | OpenPDF + Commons CSV                           |
| Big-bang foundation steps 0–6 before any UI           | Vertical slices from the start                  |

---

## Recommended order for any _new_ admin feature

1. Confirm the owning module already exposes (or can expose) the required port/use case
2. Add composition + thin bean in `admin-dashboard`
3. Add `.xhtml` page under `web/.../admin-dashboard/`
4. Protect with `@RolesAllowed("ADMIN")` and ensure `web.xml` coverage
5. Unit-test the bean; extend coverage tests if needed
6. Browser-smoke the page against Open Liberty
7. Update backlog status + release notes when the milestone is reached

---

## Definition of Done (sequence)

- [x] RBAC + dashboard KPIs
- [x] Order / Product / Customer / Refund management
- [x] Audit log + admin user management + ArchUnit
- [x] Revenue, Product Performance, Customer Insights reports
- [x] CSV/PDF export adapter (`ReportExportPort`)
- [ ] Optional: embed chart images in PDFs, richer domain metrics when the model grows

---

_This is the as-built execution record. For the original pre-implementation step list (exceptions → 27 DTOs → ports → Guava adapters → PrimeFaces), see the git history of this file._

```

```
