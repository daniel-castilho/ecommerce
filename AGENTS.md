# AGENTS.md

Modular monolith **Jakarta EE 11 + Jakarta Faces (JSF)**, multi-module Maven, **hexagonal architecture**.
Java 21. `README.md`, `docs/lessons.md` and `tasks/*` are the sources of truth; re-read the relevant parts before starting.

## Critical rules (never violate)

1. `domain/` and `application/` have **zero framework imports** (`jakarta.*`, `javax.*`, Hibernate, AWS). Verify before finishing:
   `grep -r "jakarta\|javax" <module>/src/main/java/com/loja/<module>/domain` must return **nothing**.
2. Cross-module dependencies: only `domain/port` interfaces. **Never** import `adapter` (or `application`) from another module.
3. **Zero DAO, zero `@EJB`/`@Stateless`** — repository ports + CDI `@ApplicationScoped` + `@Transactional`.
4. 100% `jakarta.*`. If you touch a file, migrate the remaining imports in the same step — never leave a file half-migrated.
5. Passwords only as **Argon2id** hashes (`UserPassword`). Never plaintext, never credentials/secrets in code or in git.
6. Don't add a new dependency without asking. Don't run a module-wide `mvn test` casually (ITs spin up Testcontainers).
7. **ALL code and ALL documentation MUST be in English.** Write every identifier, comment, Javadoc, commit message, `.md` file, and log message in English. Never write Portuguese (or any other language) in code or docs. Existing docs are already English — keep them that way.

## Commands

- Build the WAR: `mvn clean package -pl web -am` → `web/target/web.war`
- Run the app (Open Liberty, https://localhost:9443/web/): `./scripts/run-liberty.sh`
  (a `mvn clean` wipes `web/target/liberty` — features + keystore — so use this script after any clean build; see `docs/lessons.md` #6)
- Compile a module (fast): `mvn -q -pl <module> test-compile`
- Unit tests without a container (fast):
  `mvn -pl product-catalog test -Dtest='ProductTest,SkuTest,SlugTest,ProductJpaMapperTest,ProductApplicationServiceTest,CategoryTreeCacheTest,ProductHexagonalArchitectureTest' -DfailIfNoTests=false`
- Bean Validation regression (user-account, fast): `mvn -pl user-account test -Dtest='BeanValidationConstraintsTest,UserTest,UserApplicationServiceTest,UserHexagonalArchitectureTest' -DfailIfNoTests=false`
- Verify a consuming module still compiles against the ports: `mvn -q -pl order-checkout -am compile`
- Full tests (**SLOW** — spins up Postgres via Testcontainers): `mvn -pl <module> test`
- Architecture: `mvn -pl user-account test -Dtest=UserHexagonalArchitectureTest`

`*IT.java` (and the surefire plugin) run with `mvn test` by default. For fast feedback, always filter with `-Dtest=...`.

## Architecture

Each business module follows the same root package `com.loja.<module>`:

```
domain/model          entities + value objects, zero framework (e.g. User, Product, Sku)
domain/port/in        use cases (interfaces the module offers)
domain/port/out       outbound ports (RepositoryPort, PasswordHasherPort, SessionPort, ProductImageStoragePort...)
domain/exception      domain exceptions (UserNotFoundException, DuplicateSkuException...)
application/service   orchestrates the use cases; depends only on ports (DIP)
adapter/in/web        thin JSF managed beans, no business rules
adapter/out/persistence   JPA entities + mapper (toDomain/fromDomain) + port implementations
```

- `shared-kernel`: base, depends on nothing (`Money`, `Result`, `DomainEvent`).
- `admin-dashboard`: only composes use cases from other modules; zero business rules.
- `web/`: final WAR (`persistence.xml` JTA `jdbc/EcommerceDS`; dev DB credentials live in
  `web/src/main/liberty/config/server.env`, overridable via OS env `DB_*`), `.xhtml` pages,
  Facelets templates.

## Conventions

- **English only, always**: identifiers, Javadoc, comments, `.md` docs, commit messages, log messages — everything. Never write in Portuguese.
- Java 21, 4-space indent, no tabs, ~120 columns, no wildcard imports.
- Import order: `java.*` → `jakarta.*` → third-party → own packages.
- Names: classes UpperCamelCase; beans `@Named("fooBean")` lowerCamelCase; `.xhtml` kebab-case; tables/columns snake_case; tests `<Class>Test`/`<Class>IT`.
- JSF beans: `@RequestScoped` by default; `@ViewScoped`/`@SessionScoped`/`@ApplicationScoped` only with justification.
- Domain errors use `Result<T, DomainError>` in factories and domain exceptions in methods (`User.tryRegister`, `User.recordLoginFailure`).
- Mappers: static `toDomain()`/`fromDomain()` on the JPA entity (e.g. `UserJpaEntity`) or in a dedicated mapper (`ProductJpaMapper`).
- Existing patterns to reuse: Strategy for search filters (`CriteriaStrategy`/`EmailCriteriaStrategy`), observers for domain events (`AuditLogObserver`).
- UI: before writing/styling any `.xhtml`, read `docs/design-system.md` and `web/src/main/webapp/resources/css/design-tokens.css`. Never hardcode colors/spacing/px in a page; use semantic/component tokens. No new primitive/semantic token without human approval; composite components only after the rule of two (see design-system.md §4–§5).

## Testing

- JUnit 5 + AssertJ + Mockito. Naming: `method_condition_expectedResult()`.
- Unit tests in domain **without mocks** (`UserTest`, `ProductTest`).
- Adapter tests with a real DB: `*IT.java` + `AbstractIntegrationTest` (Testcontainers Postgres 15).
- IT rules (see `docs/lessons.md`):
  - Every adapter operation needs an **explicit transaction** (`inTx(...)`) — mirrors the production `@Transactional`.
  - **Close the `EntityManager` in `@AfterEach`** — an orphaned `idle in transaction` connection blocks the next test's `TRUNCATE`.
  - Pagination: `getResultList()`, **never** `getResultStream()` (it ignores `setFirstResult`/`setMaxResults`).
- **CI** (`.github/workflows/ci.yml`, GitHub Actions) is the PR gate: `unit-and-archunit` (unit + ArchUnit,
  `javax.*` guard, WAR packaging) then `integration-tests` (Testcontainers, Docker on the runner). Keep the
  fast checks green before pushing; the two status checks must pass on `main` (branch protection).

## JPA gotchas (docs/lessons.md #1)

Entity with `@OneToMany`/`@ManyToMany` (cascade) + `@Version`: the `@Version` **must round-trip in the mapper**
(domain ↔ JPA). Without it, `merge()` of a detached entity with `version=null` produces a duplicate INSERT / `PropertyValueException`.
Applying `em.find` + dirty checking is the plan B if the round-trip becomes unfeasible.

## LocalStack / S3 (product-catalog)

Product images use S3-compatible storage via LocalStack, port 4566. Use image `localstack/localstack:3.8.1`
(4.x/latest require an authentication token):

```bash
docker compose -f docker/docker-compose.yaml up -d localstack db
./scripts/bootstrap-localstack.sh   # creates product-images bucket + public-read policy (idempotent)
```

Adapter config via env/system-props: `s3.endpoint-override`, `s3.bucket`, `s3.public-base-url`, `s3.region`, `s3.access-key`, `s3.secret-key` (defaults: `http://localhost:4566`, `product-images`, `test`/`test`).

## Releases and tags

- **Do not** create a Git tag for every bugfix or chore.
- **Do** create an annotated tag when a milestone meets its Definition of Done
  (compile/tests green for touched modules + agreed smoke path).
- Before tagging:
  1. Copy `docs/releases/_template.md` → `docs/releases/v0.X.0.md` and fill it.
  2. Update the "Current state" section in `README.md` with one line pointing at that release.
  3. Promote any durable lesson into `docs/lessons.md`.
- Tag command:
  ```bash
  git tag -a v0.X.0 -m "v0.X.0 — <short title>"
  ```
- Version notes live under `docs/releases/` (versioned in Git). Do not rely on the GitHub Wiki
  as the source of truth for agents.
- Specs in `tasks/*.md` are intent *before* coding; release notes are facts *after*. If code
  diverges from a spec, update or mark the spec superseded in the same change set when practical.

## Notes

- The repository is under git (branch `main`, remote `origin` = `https://github.com/daniel-castilho/ecommerce.git`). Use `git status`/`git commit`/`git tag` normally; **do not push unless the human asks**.
- Status of the current epic (product-catalog) and pending items: "Current state" section in `README.md` and `tasks/product-catalog-*.md`.
