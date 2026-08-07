# AGENTS.md

Modular monolith built with **Jakarta EE 11 + Jakarta Faces**, multi-module Maven, **hexagonal architecture**.
Java 21.

Sources of truth: `README.md`, `docs/lessons.md`, and `tasks/*`. Re-read the relevant parts before starting any task.

## Critical rules (never violate)

1. `domain/` and `application/` have **zero framework imports** (`jakarta.*`, `javax.*`, Hibernate, AWS, etc.).
   Verify before finishing:
   `grep -r "jakarta\|javax" <module>/src/main/java/com/loja/<module>/domain` must return nothing.
2. Cross-module dependencies only through `domain/port` interfaces. **Never** import another module’s `adapter` or `application`.
3. **Zero DAO, zero `@EJB` / `@Stateless`**. Use repository ports + CDI `@ApplicationScoped` + `@Transactional`.
4. 100% `jakarta.*`. If you touch a file, migrate remaining `javax.*` imports in the same change.
5. Passwords only as **Argon2id** hashes (`UserPassword`). Never store plaintext credentials or secrets in code or git.
6. Do **not** add a new Maven dependency without explicit human approval.
7. **English only.** All identifiers, comments, Javadoc, commit messages, documentation and log messages must be in English.

## Commands

| Purpose                            | Command                                                  |
| ---------------------------------- | -------------------------------------------------------- |
| Build WAR                          | `mvn clean package -pl web -am` → `web/target/web.war`   |
| Run (Open Liberty)                 | `./scripts/run-liberty.sh` (https://localhost:9443/web/) |
| Compile a module (fast)            | `mvn -q -pl <module> test-compile`                       |
| Unit + ArchUnit only               | `mvn test -Dtest='*Test' -DfailIfNoTests=false`          |
| Full tests (slow – Testcontainers) | `mvn -pl <module> test`                                  |

> Prefer filtered unit tests. Full `mvn test` starts Postgres via Testcontainers and is slow.
> After `mvn clean`, always use `./scripts/run-liberty.sh` (it recreates the Liberty runtime).

## Architecture

Every business module follows the same package structure under `com.loja.<module>`:

```
domain/model              → Entities & Value Objects (zero framework)
domain/port/in            → Use cases the module offers
domain/port/out           → Outbound ports (repositories, external services)
domain/exception          → Domain exceptions
application/service       → Use-case implementations (depend only on ports)
adapter/in/web            → Thin JSF managed beans
adapter/out/persistence   → JPA entities + mappers + port implementations
```

- `shared-kernel` – base module, depends on nothing.
- `admin-dashboard` – only composes use cases from other modules (no business rules).
- `product-reviews` – depends only on ports from `product-catalog` and `order-checkout`.
- `web` – final WAR (`persistence.xml`, Facelets pages, shared template and design tokens).

## Conventions

- Java 21, 4-space indent, no tabs, ~120 columns, no wildcard imports.
- Import order: `java.*` → `jakarta.*` → third-party → project packages.
- Naming: classes `UpperCamelCase`, beans `@Named("fooBean")`, `.xhtml` files kebab-case, DB tables/columns snake_case, tests `*Test` / `*IT`.
- JSF beans default to `@RequestScoped`. Use `@ViewScoped` / `@SessionScoped` only with justification.
- Domain factories preferably return `Result<T, DomainError>`. Methods throw domain exceptions.
- Mappers: static `toDomain()` / `fromDomain()` on the JPA entity or a dedicated mapper class.
- **UI**: always read `docs/design-system.md` and `design-tokens.css` first. Never hard-code colors or spacing. New tokens require human approval. Composite components only after the “rule of two”.

## Testing

- JUnit 5 + AssertJ + Mockito. Method names: `method_condition_expectedResult()`.
- Domain unit tests: **no mocks**.
- Integration tests (`*IT`): use `AbstractIntegrationTest` + Testcontainers (Postgres 15).
- Mandatory IT rules:
  - Wrap every adapter operation in an explicit transaction (`inTx(...)`).
  - Close the `EntityManager` in `@AfterEach`.
  - Prefer `getResultList()` over `getResultStream()` for paginated queries.
- CI (`.github/workflows/ci.yml`) runs two stages: `unit-and-archunit` then `integration-tests`. Both must stay green.

## Releases

- Create an annotated Git tag **only** when a milestone meets its Definition of Done.
- Before tagging:
  1. Create `docs/releases/v0.X.0.md` from the template.
  2. Add a high-level entry to `CHANGELOG.md`.
  3. Update the “Current State” section in `README.md`.
  4. Promote any durable lesson to `docs/lessons.md`.
- Tag command:
  `git tag -a v0.X.0 -m "v0.X.0 — <short title>"`

## Notes

- Do **not** push to the remote unless the human explicitly asks.
- For current project status and pending work, see `README.md` and the files under `tasks/`.
