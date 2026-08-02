# Coding Standards — Java / Jakarta EE / JSF

*A practical reference for solo development. The goal: consistency across time, not ceremony. Revisit and edit this as your preferences evolve — it's a living document.*

**Relationship to `AGENTS.md`:** `AGENTS.md` is authoritative for this project's conventions (hexagonal structure, ports in `domain/port`, framework-free `domain`, release flow, commands). This file adds practical detail that doesn't fit in the top-level agent brief. Where the two conflict, `AGENTS.md` wins.

---

## 1. Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Packages | all lowercase, reverse domain, `com.loja.<module>.<layer>` | `com.loja.useraccount.domain.model` |
| Classes / Interfaces | UpperCamelCase | `ProductApplicationService`, `PasswordHasherPort` |
| Managed Beans (CDI) | UpperCamelCase class, `@Named` value lowerCamelCase | `@Named("manageProductBean")` |
| Methods / Variables | lowerCamelCase | `calculateTotal()`, `unitPrice` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| JSF pages (.xhtml) | kebab-case | `password-reset-confirm.xhtml` |
| Database tables/columns | snake_case | `user_address` |
| Test classes | `<ClassUnderTest>Test` (unit) / `<ClassUnderTest>IT` (integration) | `ProductTest`, `ProductRepositoryJpaAdapterIT` |

**Rule of thumb:** name things for what they *do* or *are*, not how they're implemented. `ProductRepositoryPort`, not `ProductDAOImpl2`.

---

## 2. Project & Package Structure

Organize by **feature (module) first, layer second**. Each business module follows:

```
com.loja.<module>
├── domain/model          # entities + value objects, zero framework imports
├── domain/port/in        # use-case interfaces the module offers
├── domain/port/out       # outbound ports (RepositoryPort, PasswordHasherPort, ...)
├── domain/exception      # domain exceptions
├── application/service   # use-case implementations (DIP: depend only on ports)
├── adapter/in/web        # thin JSF managed beans, no business rules
└── adapter/out           # persistence / storage / notification adapters + mappers
```

**Hard rule:** `domain/` and `application/` never import `jakarta.*`, `javax.*`, JPA, or AWS SDK classes. Verify with the module's ArchUnit test (e.g. `ProductHexagonalArchitectureTest`, `UserHexagonalArchitectureTest`).

---

## 3. Jakarta EE / CDI / JSF Specifics

- **100% `jakarta.*`, always.** Never introduce or reintroduce a `javax.*` import. If you touch a file with any leftover `javax.*`, migrate the whole file in the same step — never leave a file half-migrated.
- **Zero EJB, zero DAO.** No `@Stateless`, `@EJB`, `@Entity` in `domain`. Persistence goes behind a repository port implemented in `adapter/out/persistence`; orchestration goes in `@ApplicationScoped` + `@Transactional` application services.
- **Bean scope discipline.** Default to `@RequestScoped`. Use `@ViewScoped`, `@SessionScoped`, or `@ApplicationScoped` only with justification — session/application scope beans are where memory leaks and stale-state bugs live.
- **Keep managed beans thin.** A JSF bean coordinates between the view and the service layer: input binding, calling a use case, handling navigation outcomes and exception-to-`FacesMessage` translation. Business rules belong in the domain/application layers.
- **Prefer CDI injection (`@Inject`) over JNDI lookups or `new`.**
- **Facelets templating:** prefer a shared template (`<ui:insert>`) over duplicating header/footer markup per page once more than one page shares chrome (rule of two — see `docs/design-system.md`).
- **Validation:** use Bean Validation (`@NotNull`, `@Size`, etc.) on DTOs/inputs rather than scattered `if` checks in beans.
- **No business logic in `.xhtml`.** EL expressions call simple getters or bean methods — no multi-conditional chains inline.

---

## 4. Formatting

- Indentation: **4 spaces**, no tabs.
- Line length: soft limit **120 chars**.
- Braces: opening brace on same line (`if (x) {`).
- One class per file; filename matches the public class name.
- Import order: `java.*` → `jakarta.*` → third-party → own packages. No wildcard imports.
- Run a formatter before committing so this section stops being a manual concern.

---

## 5. Error Handling & Logging

- **Never swallow exceptions silently.** No empty `catch` blocks. At minimum, log with context.
- **Prefer unchecked exceptions** for programmer errors; reserve checked exceptions for recoverable conditions the caller should explicitly handle.
- **Small exception hierarchy** (domain exceptions in `domain/exception`; application errors surfaced as domain exceptions). Map to JSF `FacesMessage`s at the bean layer.
- **Logging:** use `java.util.logging` (`Logger.getLogger(...)`), which this project already uses — no extra dependency.
  - `ERROR`: something failed and needs attention.
  - `WARN`: unexpected but handled.
  - `INFO`: significant lifecycle events.
  - `DEBUG`: detail useful only while debugging.
- Log messages include enough context to diagnose without a debugger: relevant IDs, not just "error occurred".

---

## 6. Persistence (JPA)

- One `EntityManager` per request (container-managed, injected) — don't manage it manually.
- Keep JPA entities free of business logic beyond simple derived getters; put business rules in the domain model.
- Specify `fetch = FetchType.LAZY` on `@OneToMany`/`@ManyToMany` explicitly rather than relying on defaults.
- Use named/typed queries or Criteria over string-concatenated JPQL.
- Wrap multi-step writes in `@Transactional` (application service) — don't rely on implicit auto-commit.
- **`@Version` round-trips through the mapper** (domain ↔ JPA) for entities with cascading collections — see `docs/lessons.md` #1.
- Migrations: `flyway/sql/Vn__<name>.sql`, registered manually in `flyway_schema_history` (no Flyway runner in this repo).

---

## 7. Testing

- Unit test domain and services with JUnit 5 + AssertJ (Mockito for ports). These run without a container.
- Keep beans thin enough that the bean itself often needs little direct testing — test the service, not the wiring.
- Integration tests (`*IT`) use `AbstractIntegrationTest` (Testcontainers Postgres). They need explicit transactions and an `EntityManager` closed in `@AfterEach` — see `docs/lessons.md` #3.
- Naming: `methodName_condition_expectedResult()`, e.g. `reserveStock_withInsufficientStock_returnsFalse()`.
- Fast feedback: filter with `-Dtest=...` (full `mvn test` on a module spins up Testcontainers).

---

## 8. Documentation

- Javadoc on public classes/methods where the *purpose* isn't obvious from the name — skip it for trivial getters/setters.
- Comment the *why*, not the *what*.
- All documentation in English. `README.md` and `docs/*` are sources of truth — keep them updated; `tasks/*.md` specs mark superseded status when code diverges.
- Version notes live in `docs/releases/`; promote durable lessons into `docs/lessons.md`.

---

## 9. Version Control Hygiene

- Commit messages: imperative mood, short summary line, body if needed — `Fix null pointer in CheckoutService.calculateTotal`, not `fixed bug`.
- Small, focused commits over sprawling ones — easier to `git bisect` your own past self later.
- `.gitignore` build artifacts (`target/`, `.class`, IDE folders) — already done at repo root.
- **Do not push unless the human asks.** Tag releases only at milestones with Definition of Done met (annotated tag `v0.X.0`; see AGENTS.md "Releases and tags").

---

## 10. Security Basics (don't skip these even solo)

- Never trust JSF form input without server-side validation, even if client-side validation exists.
- Parameterized queries / JPA — never string-concatenate user input into JPQL or SQL.
- Escape output in `.xhtml` (JSF does this by default via `#{}` — don't disable escaping unless you know exactly why).
- Keep secrets (DB passwords, API keys) out of source control — externalize to environment variables or a config file excluded via `.gitignore`. Passwords are **Argon2id** hashes only (`UserPassword`) — never plaintext.
- RBAC: `@RolesAllowed("ADMIN")` on the bean plus a session-role page guard (`#{userBean.hasRole('ADMIN')}`) as belt-and-braces. Since 2026-08-01 the container does RBAC for real: `UserIdentityStore` (Jakarta Security `IdentityStore`) + `LoginAuthenticationMechanism` (`@AutoApplySession`, `isAuthenticationRequest()` + `notifyContainerAboutLogin`) + `HttpServletRequest.login()` establish the caller; `web.xml` security-constraints gate admin URLs (`/user-account/admin/*`, `manageProduct.xhtml`); `@RolesAllowed` and `SecurityContext.isCallerInRole(...)` resolve against real groups.

---

## Quick Pre-Commit Checklist

- [ ] Formatted (4-space indent, no wildcard imports, ~120 cols)
- [ ] No `System.out.println`, no empty catch blocks
- [ ] No `javax.*` imports anywhere in touched files
- [ ] No `@Stateless`/`@EJB`/DAO introduced
- [ ] New business logic covered by a unit test
- [ ] No secrets/credentials in the diff
- [ ] Managed bean scope justified (not defaulting to `@SessionScoped` out of laziness)
- [ ] Commit message describes *what changed and why*, not just "update"
