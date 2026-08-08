# AI Software Engineer Prompt — Admin Dashboard

**Status:** The Admin Dashboard module is largely **implemented** (through S23 report export).
This prompt is for agents that need to **extend, fix or maintain** it — not for a green-field build.

The original pre-implementation prompt (27 stories, Guava, PrimeFaces, 13 rigid steps) is preserved in git history of this file.

---

## Project context

- Modular monolith: **Jakarta EE 11 + Jakarta Faces** on **Open Liberty**
- Multi-module Maven, **hexagonal architecture**
- Java 21, English-only code and docs

**Sources of truth (read in this order):**

1. `AGENTS.md` — critical rules
2. `docs/lessons.md` — durable golden rules
3. `docs/design-system.md` — UI tokens and rule of two
4. `tasks/admin-dashboard-module-spec.md` — as-built module design
5. `tasks/admin-dashboard-backlog.md` — status of stories
6. `tasks/admin-dashboard-implementation-sequence.md` — actual delivery order
7. Existing code under `admin-dashboard/` and `web/src/main/webapp/admin-dashboard/`

---

## What this module is

`admin-dashboard` is a **composition module**:

- No business rules of its own
- No JPA entities for orders, products, customers or refunds
- Depends only on other modules’ **ports / use cases**
- Thin `@Named` JSF beans + presentation DTOs + report-export adapter

```
com.loja.admindashboard/
├── domain/          → export models + ReportExportPort + exceptions
├── application/dto/ → presentation DTOs (DashboardSummaryDTO, ChartBar, …)
└── adapter/
    ├── in/web/      → ADMIN-only managed beans
    └── out/reporting/ → ReportGeneratorAdapter (OpenPDF + Commons CSV)
```

Pages: `web/src/main/webapp/admin-dashboard/**`

---

## Non-negotiable rules

1. **Hexagonal boundaries** — never import another module’s `adapter` or `application` implementation; only `domain.port`.
2. **Zero framework in domain** — no `jakarta.*` / `javax.*` in `domain/`.
3. **RBAC** — every admin bean has `@RolesAllowed("ADMIN")`; every admin page is covered by `web.xml`.
   Guarded by `AdminAccessControlCoverageTest`.
4. **No new Maven dependency** without explicit human approval.
5. **English only** for all code, comments, commits and docs.
6. **Design system** — use tokens only; no hard-coded colors/spacing; composites only after the rule of two.
7. **Open Liberty** — use `./scripts/run-liberty.sh` after any `mvn clean`.

---

## Already delivered (do not re-implement)

| Area                                               | Status                                                     |
| -------------------------------------------------- | ---------------------------------------------------------- |
| Dashboard KPIs                                     | Done (v0.5–v0.6)                                           |
| Orders / Products / Customers / Refunds management | Done (v0.6)                                                |
| Audit log + admin user management                  | Done (v0.7)                                                |
| RBAC coverage + ArchUnit                           | Done (v0.7)                                                |
| Revenue report (S20)                               | Done (v0.8)                                                |
| Product performance report (S21)                   | Done (v0.9)                                                |
| Customer insights report (S22)                     | Done (v0.11)                                               |
| Review moderation UI                               | Done with `product-reviews` (v0.10)                        |
| CSV/PDF export (S23)                               | Adapter present (`ReportExportPort` + OpenPDF/Commons CSV) |

---

## How to add a new admin feature

1. Confirm the **owning module** already exposes (or can expose) the required port/use case.
2. Add composition logic + thin bean under `admin-dashboard/.../adapter/in/web/`.
3. Add `.xhtml` under `web/.../admin-dashboard/`.
4. Annotate the bean with `@RolesAllowed("ADMIN")` and ensure `web.xml` covers the page.
5. Unit-test the bean; keep ArchUnit and coverage tests green.
6. Browser-smoke against Open Liberty (`./scripts/run-liberty.sh`).
7. Update backlog status and, if it is a milestone, write `docs/releases/v0.X.0.md`.

**Do not:**

- Invent new query adapters that bypass owning modules
- Put business rules in admin-dashboard
- Add Guava, PrimeFaces, Chart.js or any unapproved dependency
- Use `rendered` on plain HTML elements (use `<h:panelGroup>` / `<ui:fragment>`)
- Nest DTOs inside port interfaces

---

## Useful commands

```bash
# Fast unit + ArchUnit
mvn -pl admin-dashboard -am test -Dtest='*Test' -DfailIfNoTests=false

# Full build
mvn clean package -pl web -am

# Run
./scripts/run-liberty.sh
```

After changing a dependency module, always run the consumer with `-am` so the local jar is rebuilt.

---

## Known gotchas (see also `docs/lessons.md`)

- OpenPDF colors use `java.awt.Color`, not `com.lowagie.text.Color`
- Facelets `rendered` on literal HTML is a no-op
- Locale money formatting uses non-breaking spaces (`\u00A0`)
- New JPA entities (in other modules) must be registered in `persistence.xml`
- EL requires JavaBean getters (`isX` / `getX`)

---

## When stuck

Stop and ask the human if:

- A new third-party dependency is required
- A change would put business rules inside `admin-dashboard`
- Domain data needed for a report does not exist yet (e.g. tax, cost price, margin)

Do **not** push to the remote unless the human explicitly asks.

---

_Original green-field implementation prompt (PrimeFaces, Guava, 27 DTOs, rigid 13 steps) remains available in the git history of this file._

```

```
