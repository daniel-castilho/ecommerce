# Design System — Ecommerce Monolith (Loja)

## Status

Living document. Load on demand for any task that creates or restyles a
`.xhtml` page or a UI component. Referenced from `AGENTS.md` (Conventions) so
it loads automatically for UI-generation work.

## 0. Why this file exists

The pages across the modules (`product-catalog`, `order-checkout`,
`user-account`, `admin-dashboard`) are generated from long-form prompts, mostly
by an AI agent. Today the `web` module has **no shared stylesheet and no
Facelets template**: pages use `styleClass` names that are currently undefined,
and the same visual elements (status text, form fields, tables) are re-written
per page in isolation — e.g. `styleClass="error"` is already hardcoded on the
`h:messages` of 4 pages. Left unchecked, every new module adds a new visual
dialect — this is exactly the **design-code drift** and **documentation
fatigue** failure modes described in the 2026 design-system literature (Cabin,
"Design System Best Practices That Drive Adoption"; UXPin, "15 Best Design
System Examples in 2026").

This project has no Figma file, no design tool, and no dedicated designer. The
practices below are the subset of 2026 best practice that survives that
constraint — see §8 for what is explicitly **not** adopted and why.

---

## 1. Token architecture (3 layers)

Following the primitive → semantic → component taxonomy described in
*"Design Systems in 2026: Scale UI Without the Chaos"* (Digital Applied) — the
same model behind the W3C DTCG spec, simplified here to plain CSS custom
properties since there is no design-tool pipeline to sync against.

```
Primitive   --color-primitive-blue-500: #3498db;      (raw value, never used directly)
    ↓
Semantic    --color-action-primary: var(--color-primitive-blue-500);   (named by ROLE)
    ↓
Component   --badge-font-size: var(--font-size-caption);               (scoped to 1 component)
```

**Rule:** pages and composite components reference **semantic** or **component**
tokens only. Primitive tokens are never used outside `design-tokens.css`
itself. If you're writing `color: #3498db` in an `.xhtml` or `.css` file, that's
a bug — find or add the semantic token instead.

All tokens live in one file:
**`web/src/main/webapp/resources/css/design-tokens.css`**, consumed via
**`resources/css/base.css`** (which imports the tokens and holds the shared
component styles). Both are linked by the shared Facelets template
`WEB-INF/templates/main.xhtml`; pages never link stylesheets directly (see
§10, Q1 — resolved). Never duplicate token definitions per module.

---

## 2. Naming convention

Per Atomize's *"7 Figma Design System Best Practices for 2026"* — one naming
convention shared across every module and layer, kebab-case, DTCG-style path
segments joined with `-`:

```
--{category}-{layer}-{concept}-{variant?}
```

Examples:
- `--color-primitive-gray-800` (category=color, layer=primitive, concept=gray, variant=800)
- `--color-status-cancelled` (category=color, layer=semantic, concept=status, variant=cancelled)
- `--badge-font-size` (component=badge, property=font-size)

**Status tokens are named after the domain enum values verbatim**
(`Order.Status.CANCELLED` → `--color-status-cancelled`), not after the color
they render as. This is deliberate: when a new enum value is added, the token
name is derived from the enum, not invented per-page. Values shared across
enums (e.g. `ACTIVE`, `INACTIVE` in both `ProductStatus` and `UserStatus`) map
to the same token on purpose.

---

## 3. Component inventory

Composite JSF components (Facelets tag files) that wrap a token contract.
Location: `web/src/main/webapp/WEB-INF/tags/` (registered via a
`facelet-taglib` in `WEB-INF`; exact namespace URI decided at implementation).

| Component | File | Status | Used by (real pages) | Tokens consumed |
|---|---|---|---|---|
| Status badge | `WEB-INF/tags/status-badge.xhtml` | **Built** (rule of two met) | `manageProduct.xhtml` (ProductStatus), `order-confirmed.xhtml` (Order.Status), `admin/users.xhtml` (UserStatus) | `--badge-*`, `--color-status-*` |
| Form field group | `WEB-INF/tags/form-field-group.xhtml` | **Built** (rule of two met) | login, register, password-reset, password-reset-confirm, profile, address-book, manageProduct | `--form-field-*` |
| Admin data table | `admin-data-table.xhtml` | **Candidate — not yet** (1 occurrence: `admin/users.xhtml`) | — | (not defined yet) |
| Metric card | `metric-card.xhtml` | **Candidate — not yet** (0 occurrences: no dashboard page) | — | (not defined yet) |

**Usage example** (status badge, referenced from any `.xhtml`):

```xhtml
<ui:composition xmlns:ui="jakarta.faces.facelets"
                xmlns:my="https://loja.com/design-system">
  <my:statusBadge status="#{order.status}" />
</ui:composition>
```

The tag library is declared in `WEB-INF/loja.taglib.xml` and registered via the
`jakarta.faces.FACELETS_LIBRARIES` context-param in `web.xml`.

Internally `status-badge.xhtml` maps the enum value to a CSS class
(`status-{enum-value-lowercase}`, via `fn:toLowerCase` — `jakarta.tags.functions`)
that resolves entirely through `--color-status-*` tokens — the component itself
never hardcodes a color.

This inventory is the canonical list. **Before writing a new page,** check if
the visual element you need already exists here — and honor the rule of two
(§5) before adding anything new.

---

## 4. Governance

Adapted from Cabin's tiered model ("strict governance for foundational tokens,
flexible for composite patterns") — scaled down to a solo developer working
with an AI agent instead of a multi-team org:

| Tier | Scope | Who can change it | Process |
|---|---|---|---|
| **Strict** | Primitive & semantic tokens (`design-tokens.css` Layers 1–2) | Human only | Edit the file directly, note the change in the file header comment (date + reason). No AI-authored primitive/semantic token additions without explicit human instruction. |
| **Flexible** | Component tokens (Layer 3) and composite components (`WEB-INF/tags/`) | AI agent, within a task | Allowed to add a new component token block *only* after the rule of two (§5) is met, and *only* by extending Layer 3 — never by inventing a new primitive. |

There is one owner (you). The point of formalizing this isn't multi-team
coordination — it's stopping the AI implementer from silently inventing a new
hex value mid-prompt when a token already covers the case.

---

## 5. Scope discipline — the "rule of two"

Per Parallel's *"Maintaining a Design System... 2026"*: start with a minimum
viable set, expand only from real repetition — building ahead of need is the
most common design-system failure mode for small teams.

**Rule:** a visual pattern becomes a token/component only after it appears
**identically in 2 or more places** across the already-implemented pages or
specs. Before that, style it inline and move on.

Current inventory (§3) was derived this way:

- Status badge — repeats in `order-confirmed.xhtml` (Order.Status),
  `manageProduct.xhtml` (ProductStatus) and `admin/users.xhtml` (UserStatus)
  — 3 modules ✅
- Form field group — repeats in 8 existing forms (login, register,
  password-reset, password-reset-confirm, profile, address-book, checkout,
  manageProduct) ✅
- Admin data table — **only** `admin/users.xhtml` (filters + pagination + row
  action). 1 occurrence → **do not extract yet**; revisit when a second list
  (e.g. an Order list page) exists. ⏳
- Metric card — **zero** occurrences (the `admin-dashboard` module has a
  service but no `.xhtml` page). **Do not build until the dashboard page and a
  second KPI surface exist.** ⏳

Do **not** extract a component speculatively for a module that isn't written
yet. Wait for the second occurrence.

---

## 6. Accessibility baseline

Per Onething Design's 2026 roundup: *"A design system that does not address
accessibility is incomplete."* Minimum bar for every component in §3,
non-negotiable regardless of which module consumes it:

- WCAG 2.1 AA contrast ratio (4.5:1 text, 3:1 large text/UI components) —
  verify any new semantic color token against `--color-bg-surface` and
  `--color-bg-page` before adding it.
- All interactive components (`form-field-group`, table row actions) must be
  keyboard-navigable — inherited for free from standard `h:` JSF components;
  do not replace with non-focusable `<div>`/`<span>` click handlers.
- Status conveyed by color (`status-badge`) must **also** carry the status as
  text — never color alone. Already satisfied by the current pages, which
  render the enum value as text.

---

## 7. AI compatibility (why this file is written the way it is)

Per UXPin's 2026 note: *"Systems with well-structured component APIs and clear
Design System Guidelines enable AI assistants to generate on-brand output that
respects brand rules automatically."* This is the most relevant practice for
this project specifically, since every `.xhtml` page is generated from a
long-form prompt, not typed by hand.

**Instruction for any AI implementing a new page or module:**

1. Before writing a new page, read this file and `design-tokens.css` first.
2. Check §3 (component inventory) for an existing composite component that
   covers the UI element you need. Use it — do not re-implement a status badge
   or a form field group inline.
3. If no existing token covers a color/spacing/typography value you need, do
   **not** invent a hex value or px value inline. Apply the rule of two (§5):
   if this is the first occurrence, use the nearest semantic token and flag the
   gap in the task summary for human review; if this is the second occurrence
   of an identical pattern, add a new Layer 3 component token (governance tier:
   Flexible, §4) and extract a composite component under
   `web/src/main/webapp/WEB-INF/tags/`.
4. Never add a new Layer 1 (primitive) or Layer 2 (semantic) token without
   explicit human instruction — those are Strict tier (§4).

This section is referenced from `AGENTS.md` (Conventions) so it loads
automatically for any UI-generation task.

---

## 8. Explicitly out of scope

Practices that appear in the 2026 articles but do not apply to this project's
constraints (no design tool, no multi-team org, server-rendered JSF rather than
a component framework):

| Practice | Why it's skipped |
|---|---|
| Figma-to-code sync pipeline | No Figma file exists; there is no design tool to keep in sync. |
| DTCG JSON token format | Built for cross-tool token exchange (Figma ↔ code). This project has one consumer (CSS) and one producer (this file) — plain CSS custom properties are the simpler, equivalent format. |
| Multi-team governance model with review SLAs | Solo developer + AI agent; §4's tiered model already covers the actual coordination need. |
| React/Vue component libraries (Material UI, Chakra, etc.) | Stack is Jakarta Faces (JSF), server-rendered. Composite components (§3) are the JSF-native equivalent. |
| Dedicated design-system metrics/adoption dashboards | Overhead disproportionate to a 4-module solo project. Component inventory table (§3) is sufficient tracking. |

---

## 9. Versioning

No formal semver — this is one file, one repo, one consumer. Track changes as
dated entries appended below. Any Strict-tier change (§4) must add an entry
here.

```
## Changelog
- 2026-08-01 — Initial version. Extracted status-badge and form-field-group
  (rule of two already satisfied: 3 and 8 real occurrences respectively).
  admin-data-table and metric-card are listed as candidates only, not
  extracted. Status tokens mirror the real enums Order.Status, ProductStatus
  and UserStatus.
- 2026-08-01 — Built the shared foundation: `resources/css/base.css` (imports
  the tokens + shared component styles), `WEB-INF/templates/main.xhtml`
  (shared template linking the stylesheet) and the `https://loja.com/design-system`
  tag library (`WEB-INF/loja.taglib.xml`) with real `status-badge` and
  `form-field-group` tag files, registered via `jakarta.faces.FACELETS_LIBRARIES`.
  Converted the product-catalog pages (`catalog.xhtml`, `manageProduct.xhtml`)
  to the template + tokens; public catalog now renders product cards with the
  primary image; admin create form gained an optional image upload (closes the
  S7 deviation). Q1 resolved: shared template + single base stylesheet, not
  per-page stylesheets. Remaining pages (user-account, order-checkout,
  admin/users) adopt the template in a follow-up round.
- 2026-08-01 — Rolled the shared template + tokens out to all remaining pages:
  login, register, password-reset, password-reset-confirm, profile,
  address-book, admin/users, checkout, order-confirmed. `form-field-group`
  dropped its inner `<h:message>` (repo convention is `h:messages
  showDetail="true" showSummary="false"`; a per-field message duplicated field
  errors). Pagination now renders through the `pagination` component token on
  the form element itself. Every page in the WAR now renders through the shared
  template; the storefront and admin share one visual language.
```

---

## 10. Open questions

1. ~~Should a shared Facelets template + a single base stylesheet importing
   `design-tokens.css` be introduced before the next round of pages, or is
   per-page `<h:outputStylesheet>` acceptable while the storefront and admin
   still differ visually?~~ **Resolved (2026-08-01):** shared template
   (`WEB-INF/templates/main.xhtml`) + one base stylesheet (`resources/css/base.css`,
   which `@import`s `design-tokens.css`). Rationale: the storefront and admin now
   share a visual language and the welcome page is the storefront; per-page
   stylesheet linking is exactly the drift this file exists to prevent. All new
   pages render through the template; conversion of the remaining pages is in
   progress.
2. Status tokens use one flat namespace for three enums (`Order.Status`,
   `ProductStatus`, `UserStatus`). Overlapping values (`ACTIVE`, `INACTIVE`)
   intentionally share a token. If a fourth enum is introduced later, does it
   reuse existing tokens where values overlap or get its own namespaced set?
3. No dark mode is currently planned. If it becomes a requirement, the semantic
   layer (§1) is the intended extension point — primitives stay, semantic
   tokens gain a `[data-theme="dark"]` override block. Confirm this is
   acceptable before implementation, not as an afterthought.
