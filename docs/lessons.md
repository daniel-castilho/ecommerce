# Lessons Learned

Durable register of subtle bugs and decisions that cost debugging time.
Before implementing something similar, re-read the golden rules below.

New lessons go at the **top**, with date and short context.
Full historical write-ups of every lesson remain in git history of this file.

---

## 31. Order notifications are best-effort: SMTP down must not block checkout (2026-08-10)

Phase A of the notification system: `OrderNotificationEmailAdapter` (Jakarta Mail, mirrors
`user-account`'s adapter) is the sole CDI implementation of `NotificationPort`; the former
`NotificationMockAdapter` lost its CDI scope and is now a test-only helper constructed with
`new`. The adapter catches `MessagingException`/`RuntimeException`, logs a WARNING and never
rethrows, so the checkout transaction always commits even when the mail server is unreachable —
verified live: order `8df075e8…` committed as CONFIRMED while SMTP `localhost:25` refused the
connection. Respects `UserProfile.notificationsEnabled` via `FindUserUseCase.findById(userId)`
(missing user or failed lookup defaults to sending). Trade-off: no delivery log and no retries
in Phase A; lost emails are only visible in the WARNING log.

Test note: the unit tests need a real Mail provider on the test classpath. Pin
`jakarta.mail:jakarta.mail-api:2.0.1` (provided) together with `com.sun.mail:jakarta.mail:2.0.1`
(test) — mirroring `user-account`. The bare `jakarta.jakartaee-api` aggregator pulls `jakarta.mail-api
2.1.3`, whose `StreamProvider` SPI is absent from the 2.0.1 provider, so versions must match.

## 30. Native SQL named parameters bind under Hibernate (ITs) but not EclipseLink (WAR runtime) (2026-08-10)

The Testcontainers integration tests run **Hibernate**, while the deployed WAR runs **EclipseLink**
(Eclipse Persistence Services 5.0.0). A native query using named placeholders
(`to_tsquery('english', :tsquery)`, `p.name ILIKE :like`) passed all 31 adapter ITs — then the
live catalog search 500'd: EclipseLink sent the raw `:name` literals to PostgreSQL, which raised
`syntax error at or near ":"`. The query never even ran against the FTS GIN index.

**Golden rule:** use **positional parameters** (`?N` with `setParameter(n, ...)`) in native SQL —
they bind identically on EclipseLink and Hibernate. And never treat a green Hibernate/Testcontainers
IT as proof of the runtime path: smoke the real provider (the running WAR) for any native query.

---

## 29. JSF form bindings: typed `Map` values arrive as raw Strings; never name a table `var` `view` (2026-08-09)

Two EL/JSF pitfalls that only surface in a browser, never in unit tests.

**Pitfall 1 — typed map coercion.** Binding an editable input to
`#{bean.quantityByProduct[productId]}` where `quantityByProduct` is a
`Map<String, Integer>` does **not** coerce the submitted value on this MyFaces stack.
Jakarta EL erases the map's generic value type, so the raw `String` ("3") is stored in the map;
reading it back as `Integer` (`Integer qty = map.get(id);`) throws
`ClassCastException: class java.lang.String cannot be cast to class java.lang.Integer`.
Adding `converterId="jakarta.faces.Integer"` to the input did **not** stop it in practice.

**Golden rule:** never read a JSF-submitted value straight out of a typed `Map` into a primitive
wrapper. Read it as `Object` and coerce defensively (`instanceof Integer` → `instanceof Number` →
`Integer.parseInt(String.valueOf(...))`, rejecting garbage), and keep the Integer converter on the
input as belt-and-braces. Browser-smoke every new editable binding.

**Pitfall 2 — reserved EL object names.** `var="view"` in an `h:dataTable` collides with the
reserved EL implicit object `view` (the `UIViewRoot`), so `#{view.name}` fails at render with
`jakarta.el.PropertyNotFoundException: Property [name] not found on type
[jakarta.faces.component.UIViewRoot]` — the implicit object wins over the row variable.

**Golden rule:** never name a table/`ui:repeat` var after an EL implicit object (`view`,
`request`, `session`, `application`, `facesContext`, `component`, `cc`, `param`, etc.). Use
plain names like `line`/`item`/`row`.

---

## 28. Audit-log actors resolve via a use-case port; entities belong in columns, not details (2026-08-08)

Rendering the audit log with the raw actor id is unhelpful — resolve it through a use case
(`FindUserUseCase.findById(...)` → full name) with fallbacks (subject for self-service events,
then the raw id for deleted accounts). Embedding the affected entity in free-text details makes
it unusable for filtering; record `entity_type` / `entity_id` as first-class columns.

Also: when the subject column is `NOT NULL` but the event targets a non-user entity
(product/refund), the observer previously stored the **actor** in the subject column with
`actor_id` left null — overloading semantics. Keep `user_id` populated (DB constraint) but set
`actor_id` explicitly too, so the actor column resolves for every event.

**Golden rule:** An audit row needs three clear roles — subject (`user_id`), actor (`actor_id`),
and affected entity (`entity_type`/`entity_id`). Never overload one column for two roles, and
resolve display names through a domain port, never from the raw id in the UI.

---

`#{bean.foo}` (property access) resolves `getFoo()` / `isFoo()`, but `#{bean.foo(x)}`
(method invocation with an argument) must match a method literally named `foo`.
A boolean method `isInWishlistFor(String)` is found as a property but **not** as the
method `inWishlistFor(...)` — at render time that fails with
`jakarta.el.MethodNotFoundException: Method not found: ...inWishlistFor(java.lang.String)`,
visible only when the page loads in a browser (no unit test catches it).

**Golden rule:** Name the Java method exactly as the EL call, e.g. `public boolean
inWishlistFor(String id)`. Keep the `is`/`get` prefix only for no-arg bean-property
getters. Browser-smoke any new EL call with arguments.

## 26. `h:link` hrefs already include the context path — and dropins can serve a stale WAR (2026-08-07)

`h:link outcome="/wishlist/wishlist.xhtml"` renders a context-relative `href` that begins with the
app context path (`/web/...`). Browser-QA scripts that string-prefix the base URL
(`https://localhost:9443/web` + href) end up hitting `/web/web/...` and getting 404s. Separately,
copying a freshly built WAR over `dropins/` while the app is running can leave the old version
served, so a smoke failure may be pointing at stale code.

**Golden rule:** Build page URLs from the captured `href` directly
(`https://localhost:9443${href}`). When a browser check "should" work, verify the deployed page
matches the source before debugging the app.

## 25. `h:messages globalOnly="true"` renders the summary only (2026-08-07)

`h:messages` defaults to `showSummary="true"` and `showDetail="false"`. With `globalOnly` (the
common template pattern) a bean message like `new FacesMessage(INFO, "Wishlist", "Product added to
your wishlist.")` renders as just *"Wishlist"* — the important text never appears.

**Golden rule:** Put the user-facing text in the **summary** and keep the empty detail, matching
the page's existing convention (e.g. `ProductReviewBean` on `product-detail.xhtml`). Use
`showDetail="true" showSummary="false"` only on dedicated form pages like login/checkout.

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
