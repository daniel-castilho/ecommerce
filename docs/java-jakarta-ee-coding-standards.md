# Coding Standards — Java / Jakarta EE / JSF

Practical reference for solo and AI-assisted development. Goal: **consistency over time**, not ceremony. Living document — edit as the project evolves.

**Relationship to other docs:**

| Doc | Wins when |
|-----|-----------|
| `AGENTS.md` | Project conventions, release flow, hard agent rules |
| **This file** | Day-to-day coding detail that does not fit in AGENTS |
| `docs/lessons.md` | Durable rules learned the hard way |

Where this file conflicts with `AGENTS.md`, **`AGENTS.md` wins**.

---

## 1. Naming

| Element | Convention | Example |
|---------|------------|---------|
| Packages | lowercase, `com.loja.<module>.<layer>` | `com.loja.useraccount.domain.model` |
| Classes / interfaces | UpperCamelCase | `ProductApplicationService`, `PasswordHasherPort` |
| CDI beans | Class UpperCamelCase; `@Named` lowerCamelCase | `@Named("manageProductBean")` |
| Methods / variables | lowerCamelCase | `calculateTotal()`, `unitPrice` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| JSF pages | kebab-case | `password-reset-confirm.xhtml` |
| DB tables / columns | snake_case | `user_address` |
| Tests | `*Test` (unit) / `*IT` (integration) | `ProductTest`, `ProductRepositoryJpaAdapterIT` |

Name for **what it is or does**, not the implementation: `ProductRepositoryPort`, not `ProductDAOImpl2`.

---

## 2. Package structure (feature first, layer second)

```
com.loja.<module>
├── domain/model          # entities + VOs — zero framework imports
├── domain/port/in        # use-case interfaces
├── domain/port/out       # repository, hasher, mail, …
├── domain/exception
├── application/service   # use-case implementations (depend on ports only)
├── application/dto       # commands / results — never nested inside ports
├── adapter/in/web        # thin JSF beans
└── adapter/out           # JPA, S3, notification, mocks + mappers
```

**Framework boundary (as enforced by ArchUnit):**

| Layer | Framework imports |
|-------|-------------------|
| `domain/` | **None** — no `jakarta.*`, `javax.*`, JPA, AWS SDK |
| `application/` | **Minimal CDI/JTA only** (`@ApplicationScoped`, `@Transactional`) when needed; no JPA entities, no Faces, no AWS |
| `adapter/` | Full framework stack allowed |

Cross-module access is **ports only**. `admin-dashboard` is composition-only (no own business rules for orders/products/users).

---

## 3. Jakarta EE / CDI / JSF

- **100% `jakarta.*`.** Never reintroduce `javax.*`. If a file still has leftovers, migrate the whole file in the same change.
- **Zero EJB / DAO.** No `@Stateless`, `@EJB`. No `@Entity` in `domain`. Persistence behind repository ports in `adapter/out`.
- **Bean scope.** Default `@RequestScoped`. Use `@ViewScoped` / `@SessionScoped` / `@ApplicationScoped` only with a reason (multi-step forms, session user, caches).
- **Thin managed beans.** Bind input → call use case → navigation / `FacesMessage`. No business rules in beans or `.xhtml`.
- Prefer `@Inject` over JNDI or `new` for application services.
- **Facelets:** shared template once chrome is shared twice (`docs/design-system.md`, rule of two).
- **`rendered`:** only on JSF components (`h:panelGroup`, `ui:fragment`), never on raw HTML — otherwise it is a no-op.
- **EL:** JavaBean getters only (`isX` / `getX`). Prefer numeric character references in XHTML (`&#171;`) over named entities that Facelets may not resolve.
- **Bean Validation:** put `@NotNull` / `@Size` / … on **adapter-layer beans or view models**, not on domain types (keeps domain free of `jakarta.validation`). Enable empty-field validation in `web.xml` when using `@NotBlank`.
- No multi-branch business logic in EL.

---

## 4. Formatting

- 4 spaces, no tabs
- Soft line length ~120
- Opening brace on the same line
- One public top-level type per file
- Imports: `java.*` → `jakarta.*` → third-party → own packages; **no wildcards**
- Format before commit (IDE or project formatter — keep diffs boring)

---

## 5. Errors & logging

- Never empty `catch`. Log with context (ids, operation), not only `"error occurred"`.
- Prefer unchecked exceptions for domain/application failures; map to `FacesMessage` in the bean.
- Domain exceptions live under `domain/exception`.
- Logging: **`java.util.logging`** only (no extra logging dependency).
  - `SEVERE` / error: needs attention
  - `WARNING`: handled anomaly
  - `INFO`: significant lifecycle
  - `FINE` / debug: diagnostic detail

Never log passwords, tokens, or full card data.

---

## 6. Persistence (JPA)

- Container-managed `EntityManager` (injected); do not open long-lived EMs in app code.
- JPA entities are persistence records — business invariants stay in the domain model.
- Explicit `FetchType.LAZY` on collections; avoid N+1 (join fetch / entity graph where needed).
- Criteria or typed/named queries — never concatenate user input into JPQL/SQL.
- Multi-step writes: `@Transactional` on the **application** service.
- **`@Version` must round-trip** domain ↔ JPA in the mapper (see `docs/lessons.md`).
- **New entities:** register in the WAR `persistence.xml` when `exclude-unlisted-classes` is true.
- Schema: Flyway scripts under the project’s migration path; follow the **current** repo process (README / existing `V*__*.sql` pattern). Prefer a rollback note for non-trivial DDL.

Integration tests: explicit transactions helper, close `EntityManager` in `@AfterEach` (`docs/lessons.md`).

---

## 7. Testing

| Kind | Tooling | Notes |
|------|---------|--------|
| Domain | JUnit 5, no mocks | Pure invariants and state machines |
| Application | JUnit 5 + mocked ports | AssertJ / Mockito as already used |
| Adapters | `*IT` + Testcontainers | Postgres (and LocalStack where relevant) |
| Architecture | ArchUnit per module | Ports are interfaces; DTOs not nested in ports |

- Method names: `method_condition_expectedResult`
- Fast loop: `-Dtest='*Test'` to skip containers
- After WAR-level changes: smoke on Open Liberty (`./scripts/run-liberty.sh`)

---

## 8. Documentation

- Javadoc where purpose is not obvious from the name; skip trivial getters.
- Comment **why**, not what.
- English only for code, comments, commits, and docs.
- Releases: `docs/releases/v0.X.0.md`. Durable rules: `docs/lessons.md`. Epic status: `tasks/*`.

---

## 9. Version control

- Imperative commit subject: `Fix null pointer in checkout total calculation`
- Small, focused commits
- Do **not** push unless the human asks
- Annotated tags only at milestones with DoD met (`v0.X.0` — see `AGENTS.md`)

---

## 10. Security

- Server-side validation always; never trust the browser alone.
- Parameterized JPA/SQL only.
- Default EL escaping in Facelets — do not disable without a documented reason.
- Secrets via environment / excluded config — never in git. Passwords: **Argon2id** hashes only.
- **RBAC (current):** Jakarta Security `IdentityStore` + authentication mechanism + `HttpServletRequest.login()`; `web.xml` constraints for admin URLs; `@RolesAllowed` on admin beans; `SecurityContext` / `UserBean.hasRole` for EL. Extend this model — do not invent a second one.
- HTML in user content (e.g. review body, product description): sanitize with the project’s existing OWASP helper pattern.

---

## Quick pre-commit checklist

- [ ] Formatted; no wildcard imports
- [ ] No `System.out.println`; no empty catch
- [ ] No `javax.*` in touched files
- [ ] No `@Stateless` / `@EJB` / DAO
- [ ] Domain free of framework; DTOs in `application/dto`
- [ ] New entity registered in `persistence.xml` if needed
- [ ] Unit test for new domain/application behaviour
- [ ] No secrets in the diff
- [ ] Bean scope justified
- [ ] Commit message says what and why

---

*Earlier wording that required zero `jakarta.*` in `application/` (stricter than ArchUnit and the codebase) is superseded by the boundary table in §2.*
```
