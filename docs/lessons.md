# Lessons Learned

Durable register of subtle bugs and decisions that cost debugging time.
Before implementing something similar, re-read the golden rules below.

New lessons go at the **top**, with date and short context.
Full historical write-ups of every lesson remain in git history of this file.

---

## 24. A fresh Liberty runtime needs sibling jars installed, and the first feature download is silent (2026-08-07)

After `mvn clean`, `mvn -pl web liberty:create` resolves sibling modules from `~/.m2` — a `package`
(not `install`) run leaves jars missing and the script fails. Also, the first `liberty:create` /
feature install downloads the web profile with no progress under `-q`: it can look frozen for
minutes.

**Golden rule:** Build the fresh runtime with `mvn -pl web -am install -DskipTests` and don't kill
a `liberty:create` that appears stuck — watch for network activity or drop `-q` on the first run.

## 23. PostgreSQL `TIMESTAMP` stores microseconds (2026-08-07)

JPA `Instant` mapped to `TIMESTAMP` survives the round-trip with microsecond truncation. Asserting
exact `Instant` equality in an IT is flaky.

**Golden rule:** In ITs compare instants with a tolerance, e.g.
`assertThat(Duration.between(expected, actual).abs()).isLessThan(Duration.ofNanos(1000))`.

## 22. Facelets is strict XML — named HTML entities abort the page parse (2026-08-07)

`&mdash;`, `&nbsp;`, `&copy;` … are valid in HTML but **not** in XML; Facelets refuses to parse a
page that contains them (only the five predefined XML entities and numeric entities are allowed).
Pre-existing pages carried these and only surfaced as parse errors at runtime, not in any test.

**Golden rule:** In `.xhtml` use the Unicode character (— or U+00A0) or a numeric entity (`&#8212;`).
After a full build, browser-smoke every touched page — the XML parser only runs when the page loads.

## 21. OpenPDF uses `java.awt.Color`, not `com.lowagie.text.Color` (2026-08-06)

OpenPDF 1.3.x (the Apache-2.0 fork) dropped the old iText color types.
`PdfPCell.setBackgroundColor(...)` and font factories expect `java.awt.Color`.

**Golden rule:** When adopting OpenPDF, compile against the real signatures. Keep the library dependency inside an adapter (`ReportExportPort`) so domain stays clean.

---

## 20. `rendered` on a plain HTML element is a Facelets no-op (2026-08-05)

`rendered="#{...}"` is honored only on JSF / Facelets components (`h:*`, `ui:*`, `my:*`).
On a literal `<div rendered="...">` Facelets treats it as a pass-through attribute — the element always appears.

**Golden rule:** Gate conditional content with `<h:panelGroup layout="block" rendered="...">` or `<ui:fragment rendered="...">`. If a “hidden” element still shows up, look for a literal `rendered="..."` in the served HTML.

---

## 19. A new module’s first browser smoke surfaces WAR-level gotchas (2026-08-05)

Unit + ArchUnit + ITs can be green while the integrated WAR fails. Common failures:

1. New JPA entity missing from `persistence.xml` (`exclude-unlisted-classes=true`).
2. EL property not a JavaBean getter (`hasReviews()` → must be `isHasReviews()` / `getHasReviews()`).
3. Facelets is strict XML — use numeric entities (`&#171;`) instead of `&laquo;`.
4. `f:convertDateTime` does not support `java.time.Instant` — expose a formatted `String` from the bean.
5. EclipseLink L2 cache can serve stale entities after out-of-band data fixes → restart the server.

**Golden rule:** Always browser-smoke every new page/action after integrating a module into the WAR. Never trust a green module suite alone.

---

## 18. Locale-formatted strings and unordered maps make assertions flaky (2026-08-05)

- `NumberFormat` (pt-BR) inserts a non-breaking space (`\u00A0`).
- `Map.of` / `HashMap` iteration order is undefined.

**Golden rules:**

1. Normalize `\u00A0` (or assert on `BigDecimal`) instead of hard-coded rendered literals.
2. If display order matters, sort in production code (or in the assertion).
3. A test that passes once and fails on the next run is usually an ordering assumption.

---

## 17. Guard cross-cutting web contracts with source-scan tests (2026-08-03)

RBAC lives in two places (`web.xml` security-constraints + `@RolesAllowed`).
The same drift risk exists for status-badge CSS vs domain enums.

**Golden rule:** When a contract has two hand-maintained layers, write a fast source-scan test in the `web` module that walks both directions (missing coverage **and** dead coverage). Prefer structure-derived inventories over explicit file lists.

See: `AdminAccessControlCoverageTest`, `StatusBadgeCssCoverageTest`.

---

## 16. Changing a domain state-machine rule breaks old transition-matrix tests (2026-08-03)

Editing `ALLOWED_TRANSITIONS` without updating the corresponding domain tests leaves the suite red at release time.

**Golden rule:** When you change a state-machine rule, grep the domain tests for assertions on the **old** behaviour and update them in the same commit. Run the module’s fast unit suite before tagging.

---

## 15. Cross-module test classpath uses the _installed_ dependency jar (2026-08-03)

`mvn -pl admin-dashboard test` resolves sibling modules from the local `.m2` repository.
If that jar is stale, tests fail to discover (`NoClassDefFoundError`).

**Golden rule:** After adding classes to a dependency module, run consumer tests with `-am` so the dependency is rebuilt from source.

---

## 14. Deterministic tests on aggregates: use `reconstitute`, not the random-id factory (2026-08-03)

Domain factories that call `UUID.randomUUID()` produce a different id every time.
Tests that assert on identity must use the reconstitution path.

**Golden rule:**

- `request(...)` / `create(...)` → new aggregate (random id)
- `reconstitute(id, ...)` → restore from persistence (fixed id)
  Use the latter in tests that care about identity or pagination math.

---

## 13. Explicit transactions + close EntityManager in integration tests

Adapter ITs must mirror production `@Transactional` behaviour.

**Golden rules:**

1. Wrap every adapter operation in `inTx(...)`.
2. Close the `EntityManager` in `@AfterEach` — an orphaned “idle in transaction” connection blocks the next test’s `TRUNCATE`.
3. Prefer `getResultList()` over `getResultStream()` for paginated queries (streams ignore `setFirstResult` / `setMaxResults`).

---

## 12. `@Version` must round-trip in the mapper (JPA optimistic locking)

When an entity has `@Version` + cascading collections, the version value must be mapped both ways (domain ↔ JPA).
A detached entity with `version = null` causes a duplicate `INSERT` / `PropertyValueException` on `merge()`.

**Golden rule:** Always map `@Version` in `toDomain()` / `fromDomain()`. Plan B is `em.find` + dirty checking.

---

## 11. Design-system “rule of two”

A visual pattern becomes a token or composite component only after it appears identically in **two or more** places.
Until then, style it inline with existing semantic tokens.

**Golden rule:** Never invent new primitive/semantic tokens without human approval. Never extract a component speculatively.

---

## 10. Liberty + programmatic login

Open Liberty (with a JASPI mechanism active) forbids `HttpServletRequest.login()`.
Use `SecurityContext.authenticate(...)` instead.

Also: after Liberty 26.0.0.4 the default LTPA key password was removed — set `<ltpa keysPassword>` explicitly.

---

## 9. English only

All identifiers, comments, Javadoc, commit messages, documentation and log messages must be in English.
Never write Portuguese (or any other language) in code or docs.

---

## 8. Zero framework in domain / application

`domain/` and `application/` must contain **zero** `jakarta.*` / `javax.*` imports.
ArchUnit enforces this. Verify with:

```bash
grep -r "jakarta\|javax" <module>/src/main/java/com/loja/<module>/domain
```

````

---

## 7. DTOs belong in `application/dto`, not nested inside ports

Nesting records inside port interfaces violates ArchUnit boundary rules and couples the contract to a specific shape.
Keep summary/command DTOs in `application/dto`.

---

## Older lessons (condensed)

| #   | Topic                                | Golden rule                                                          |
| --- | ------------------------------------ | -------------------------------------------------------------------- |
| 6   | Liberty runtime wiped by `mvn clean` | Always use `./scripts/run-liberty.sh` after a clean build            |
| 5   | S3 / LocalStack                      | Pin `localstack/localstack:3.8.1`; use `forcePathStyle(true)`        |
| 4   | Bean Validation on JSF beans         | Keep `jakarta.validation` out of domain; annotate adapter beans only |
| 3   | IT harness / Testcontainers          | Prefer filtered unit tests; full `mvn test` is slow                  |
| 2   | Password hashing                     | Argon2id only (`UserPassword`); never plaintext                      |
| 1   | Hexagonal boundaries                 | Modules depend only on other modules’ `domain.port` interfaces       |

---

_Full narrative write-ups of every lesson (symptoms, root-cause analysis, files involved) are preserved in the git history of this file._

```

---

This version:

- Keeps every durable golden rule that agents still need
- Removes long narrative / file-list noise
- Adds the same “historical detail lives in git” note you liked
- Is fully in English and much easier to load into context
```
````
