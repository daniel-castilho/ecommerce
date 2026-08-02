# Coding Standards — Java / J2EE / JSF / Jakarta EE

*A practical reference for solo development. The goal: consistency across time, not ceremony. Revisit and edit this as your preferences evolve — it's a living document.*

> **Context note (July 30, 2026):** the "v2" box below describes a **different codebase** — the legacy `java-ee-online-shop` app (GlassFish 5, `catalog-core`/`catalog-adapters`/`catalog-web/servlet`/`ear`) mid-migration to Jakarta EE. The current repo is the **`ecommerce` monolith** (Jakarta EE 11, multi-module: `shared-kernel`, `user-account`, `product-catalog`, `order-checkout`, `admin-dashboard`, `web`), where module conventions are: hexagonal, package root `com.loja.<module>`, ports in `domain/port/in` + `domain/port/out`, framework-free `domain`, thin JSF beans in `adapter/in/web`, Strategy pattern for conditional/algorithm families. The **general conventions** (naming, structure, etc.) still apply; the repo-specific claims in this document (Section 11, module layout) refer to the legacy project and should not be applied to `ecommerce`.

> **v2 — tailored to `java-ee-online-shop`.** This revision reflects your actual project: a Java EE 8 / JSF 2.x legacy app (GlassFish 5 baseline, multi-module Maven: `catalog-core`, `catalog-adapters`, `catalog-web/servlet`, `ear`, `projects/logging`) mid-migration to Jakarta EE 10/11, per your `AGENTS.md` and `docs/action_plan.md`. Standards below assume that migration context, not a greenfield app. Section 11 lists concrete issues found in the current codebase.

---

## 1. Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Packages | all lowercase, reverse domain | `com.yourorg.appname.service` |
| Classes / Interfaces | UpperCamelCase | `InvoiceService`, `Payable` |
| Managed Beans (CDI) | UpperCamelCase class, `@Named` value lowerCamelCase | `@Named("invoiceBean")` |
| Methods / Variables | lowerCamelCase | `calculateTotal()`, `unitPrice` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| JSF pages (.xhtml) | kebab-case | `invoice-detail.xhtml` |
| JSF managed bean names | lowerCamelCase, meaningful suffix | `invoiceListBean`, `userEditBean` |
| Database tables/columns | snake_case (if you control schema) | `invoice_line_item` |
| Test classes | `<ClassUnderTest>Test` | `InvoiceServiceTest` |

**Rule of thumb:** name things for what they *do* or *are*, not how they're implemented. `InvoiceRepository`, not `InvoiceDAOImpl2`.

---

## 2. Project & Package Structure

Organize by **feature/domain first, layer second** — easier to navigate solo than pure horizontal layering once the app grows past a handful of entities.

```
com.yourorg.appname
├── invoice/
│   ├── Invoice.java              (entity)
│   ├── InvoiceRepository.java    (persistence)
│   ├── InvoiceService.java       (business logic)
│   └── InvoiceBean.java          (JSF managed bean / controller)
├── user/
│   └── ...
├── common/
│   ├── exception/
│   ├── util/
│   └── validation/
└── config/
    ├── PersistenceConfig.java
    └── SecurityConfig.java
```

Avoid a single flat `com.yourorg.appname` package with 80 classes in it, and avoid the opposite extreme of over-layering (`dto/`, `impl/`, `interface/` as top-level siblings) — both make solo navigation harder than it needs to be.

### 3.1 Your actual module layout

Your project already uses a **layered multi-module split**, not the feature-first layout above — and that's fine to keep, since restructuring it isn't part of the migration goal:

```
java-ee-online-shop/
├── catalog-core/   # shared models, JPA entities, exceptions (api.model, api.entity, api.exception)
├── catalog-adapters/              # EJBs, services, DAOs, mappers, MDBs, persistence.xml
├── catalog-web/servlet/  # WAR: JSF managed beans + XHTML views
├── projects/logging/  # shared logging module
└── ear/               # EAR assembly / server config
```

**Hard rule going forward: every new class lives inside its module's proper path.** The dump currently has several loose top-level files — `ProductBean.java`, `LoginBean.java`, `CustomExceptionHandler.java`, `centralizedExceptionHandler.java`, `securityConfig.java`, `login.xhtml`, `faces-config.xml` — sitting outside `catalog-web/servlet/src/main/...` entirely. These look like editor scratch copies or duplicates of files that already exist properly inside the module (there's already a `ProductBean.java` inside `catalog-web/servlet/src/main/java/.../web/jsf/beans/`). Before writing new code, reconcile which copy is canonical and delete the stray one — two versions of the same class silently drifting apart is one of the easiest ways to lose an afternoon.

---

## 3. Jakarta EE / CDI / JSF Specifics

- **`javax.*` → `jakarta.*` is a one-way door.** Once a file is touched, all its imports move to `jakarta.*` in the same commit — never leave a file half-migrated (`jakarta.persistence` next to `javax.faces` in the same class). Your codebase is already inconsistent here across modules; treat any file you edit as an opportunity to finish its migration, not just fix the thing you came for.
- **Bean scope discipline.** Default to `@RequestScoped` unless you have a specific reason for `@ViewScoped`, `@SessionScoped`, or `@ApplicationScoped`. Session/application scope beans are where memory leaks and stale-state bugs live — justify every one with a comment.
- **Keep managed beans thin.** A JSF bean should coordinate between the view and the service layer — validation of input, calling a service method, handling navigation outcomes. Business logic belongs in `@ApplicationScoped`/`@Stateless` service classes, not in the bean.
- **Prefer CDI injection (`@Inject`) over JNDI lookups or `new`.** Keeps things testable and consistent.
- **EJBs vs CDI beans:** if you don't need container-managed transactions, remote invocation, or timers, a `@ApplicationScoped` CDI bean is simpler than a `@Stateless` EJB. Reach for EJB features only when you actually need them.
- **Facelets templating:** use a consistent template (`template.xhtml` with `<ui:insert>`) rather than duplicating header/footer markup per page.
- **Validation:** use Bean Validation (`@NotNull`, `@Size`, etc.) on entities/DTOs rather than manual `if` checks scattered in beans.
- **No business logic in `.xhtml` files.** EL expressions should call simple getters or bean methods — not chain multiple conditionals inline.

---

## 4. Formatting

- Indentation: **4 spaces**, no tabs.
- Line length: soft limit **120 chars**.
- Braces: opening brace on same line (`if (x) {`).
- One class per file, filename matches public class name.
- Import order: `java.*` → `jakarta.*` → third-party → your own packages. No wildcard imports.
- Use a formatter (Eclipse/IntelliJ built-in, or `google-java-format`) and run it before every commit so this section stops being a manual concern.

---

## 5. Error Handling & Logging

- **Never swallow exceptions silently.** No empty `catch` blocks. At minimum, log with context.
- **Use checked exceptions sparingly** — prefer unchecked (`RuntimeException` subclasses) for programmer errors; reserve checked exceptions for recoverable conditions the caller should explicitly handle.
- **Define a small hierarchy of application exceptions** (e.g. `AppException` → `ValidationException`, `NotFoundException`) rather than throwing raw `RuntimeException` everywhere. Map these to JSF `FacesMessage`s at the bean layer.
- **Logging:** your codebase currently uses `java.util.logging` (`Logger.getLogger(...)`) throughout, plus at least one `System.out.println`/`printStackTrace()` pair in `CustomExceptionHandler`. Decide once, not per-file: either standardize on JUL (zero extra dependency, fine for a solo lab project) or migrate to SLF4J via the existing `projects/logging` module. Either is acceptable — but pick one and eliminate `System.out`/`printStackTrace` everywhere, since those bypass whatever log level/handler config you set up.
  - `ERROR`: something failed and needs attention.
  - `WARN`: unexpected but handled.
  - `INFO`: significant lifecycle events (startup, major operations).
  - `DEBUG`: detail useful only while actively debugging.
- Log messages should include enough context to diagnose without a debugger: relevant IDs, not just "error occurred."

---

## 6. Persistence (JPA)

- One `EntityManager` per request (container-managed, injected) — don't manage it manually unless you have a specific reason.
- Keep entities free of business logic beyond simple derived getters; put business rules in services.
- Always specify `fetch = FetchType.LAZY` on `@OneToMany`/`@ManyToMany` explicitly rather than relying on defaults — avoids accidental N+1 queries.
- Use named/typed queries (`@NamedQuery`, Criteria API, or a query-builder) over string-concatenated JPQL.
- Wrap multi-step writes in a clearly demarcated transaction (`@Transactional` or container-managed) — don't rely on implicit auto-commit behavior for anything with more than one write.

---

## 7. Testing

- Unit test service/business logic with JUnit 5 + Mockito — these should run without a container.
- For JSF beans, keep enough logic delegated to services that the bean itself often doesn't need heavy testing — test the service, not the wiring.
- Integration tests (Arquillian, or a lightweight embedded container) are worth it once persistence/transaction behavior matters — don't feel obligated to set this up on day one of a solo project, but don't skip it once the app has real data integrity requirements.
- Naming: `methodName_condition_expectedResult()`, e.g. `calculateTotal_withDiscount_appliesPercentageCorrectly()`.

---

## 8. Documentation

- Javadoc on public classes/methods where the *purpose* isn't obvious from the name — skip it for trivial getters/setters.
- A `README.md` per module/major package explaining what it's for and any non-obvious setup (env vars, config files needed).
- Comment the *why*, not the *what* — code should explain what it does; comments explain decisions that aren't obvious from reading it (e.g. "using pessimistic lock here because concurrent invoice edits caused double-billing in testing").

---

## 9. Version Control Hygiene

- Commit messages: imperative mood, short summary line, body if needed — `Fix null pointer in InvoiceService.calculateTotal`, not `fixed bug`.
- Small, focused commits over sprawling ones — easier to `git bisect` your own past self later.
- `.gitignore` build artifacts (`target/`, `.class` files, IDE-specific folders).
- Tag releases if you deploy in versions, even informally (`v0.3.0`).

---

## 10. Security Basics (don't skip these even solo)

- Never trust JSF form input without server-side validation, even if client-side validation exists.
- Parameterized queries / JPA — never string-concatenate user input into JPQL or SQL.
- Escape output in `.xhtml` (JSF does this by default via `#{}` — don't disable escaping unless you know exactly why).
- Keep secrets (DB passwords, API keys) out of source control — externalize to environment variables or a config file excluded via `.gitignore`.

---

## 11. Legacy Code Review — Findings from Current Codebase

Concrete issues spotted in `projeto_completo.txt`, worth fixing opportunistically as you touch each area (not necessarily a dedicated cleanup pass):

1. **Duplicate/stray files outside module structure.** `ProductBean.java`, `LoginBean.java`, `CustomExceptionHandler.java`, `centralizedExceptionHandler.java`, `securityConfig.java`, `login.xhtml`, `faces-config.xml` exist at the repo root alongside (or instead of) their proper module locations. Reconcile and remove duplicates — see §3.1.
2. **`ProductBean` is `@SessionScoped`.** Per §3's scope rule, this should be justified or downgraded to `@ViewScoped`/`@RequestScoped`. A session-scoped bean holding a mutable `ProductModel` across the whole session is a likely source of stale-state bugs (e.g. leftover edit state after navigating away).
3. **`CustomExceptionHandler.handle()` uses `System.out.println` and `t.printStackTrace()`** instead of a logger — exactly the anti-pattern §5 calls out, and ironically it's in your *exception handling* infrastructure, so failures there are currently invisible to any real log pipeline.
4. **`ProductDao` has no CDI/EJB bean annotation** (`@Stateless`, `@ApplicationScoped`, etc.) — as a plain class it can't be reliably injected via `@Inject`/`@EJB` by the container in the way the rest of the EJB layer expects. Worth confirming whether it's currently injected at all or instantiated manually somewhere.
5. **Raw `List` type with `@SuppressWarnings("unchecked")` in `ProductDao.list()`** — `Query.getResultList()` returns a raw `List`; wrap it in `entityManager.createNamedQuery("ProductEntity.getAll", ProductEntity.class)` (typed query) to drop the raw type and the suppression entirely.
6. **Commented-out dead code left in place** (e.g. `ProductBean.initialize()` has commented sample data, `ProductDao.list()` has a commented-out unnamed-query alternative). Per your action plan's "baseline discovery" phase, either delete it or move it to a `docs/lessons.md` note if it's there for migration reference — don't let it linger inline.
7. **`SecurityConfig` already uses `jakarta.security.enterprise.*`** (Jakarta Security API) while beans elsewhere still import `javax.*` — a good example of the exact half-migrated state §3's first rule addresses. This file is actually ahead of the rest of the codebase; use it as the reference pattern for what "fully migrated" looks like when doing the JSF-bean pass.

None of these block progress — they're exactly the kind of thing your `action_plan.md` Phase 1 ("baseline discovery") and Phase 8 ("functional validation and cleanup") are meant to catch. Worth a `tasks/todo.md` entry each if you want to track them formally.

---

## Quick Pre-Commit Checklist

- [ ] Formatted (4-space indent, no wildcard imports)
- [ ] No `System.out.println` or empty catch blocks
- [ ] New business logic covered by a unit test
- [ ] No secrets/credentials in the diff
- [ ] Managed bean scope justified (not defaulting to `@SessionScoped` out of laziness)
- [ ] Commit message describes *what changed and why*, not just "update"
- [ ] If touched, file's imports are fully `jakarta.*` (no leftover `javax.*` in the same file)
- [ ] No stray duplicate class living outside its proper module path

---

*Suggestion: if you want, I can also generate an IDE-ready formatter config (Eclipse/IntelliJ XML) or a `checkstyle.xml` that enforces sections 1 and 4 automatically — just say the word.*
