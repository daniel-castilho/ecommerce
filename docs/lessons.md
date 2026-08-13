# Lessons Learned

Durable register of subtle bugs and decisions that cost debugging time.
Before implementing something similar, re-read the golden rules below.

New lessons go at the **top**, with date and short context.
Full historical write-ups of every lesson remain in git history of this file.

---

## 42. A per-user cap is a count against an append-only ledger, not a counter column (2026-08-11)

The global coupon cap is a lock-serialized read-modify-write on `used_count` (lesson 40).
Extending that idea to a per-user cap would mean a counter *per user* — but the cap needs a
user dimension and an audit trail anyway. **Golden rules:**

1. Model per-user usage as an append-only ledger table (`tb_coupon_redemption`), one row per
   (coupon, user, redeemed_at), indexed on `(coupon_id, user_id, redeemed_at)`.
2. Enforce the cap by `COUNT`ing rows for (coupon, user) under the same pessimistic write
   lock already held for the global counter — serializing both under one lock keeps the
   invariant without a second lock.
3. Never pre-aggregate per-user counts into a mutable column; the ledger is simpler, is
   race-free by construction, and doubles as audit data.

## 42. Inspect a multipart MimeMessage only after saveChanges() (2026-08-13)

Building a `MimeMessage` with a `MimeMultipart("alternative")` and reading it back via
`msg.getContent()` before saving collapses both parts to `text/plain` (the subtype of the
second part is lost). `Transport.send()` calls `saveChanges()` internally, so the shipped
message is correct — the trap only shows up in unit tests that read the message before sending.

**Golden rule:** when a test asserts on the parts of a composed MIME message, call
`msg.saveChanges()` first, then read `getContent()`. Mirror the real send path.

---

## 41. Thread raw per-line data through the port; don't pre-aggregate to a scalar (2026-08-11)

`QuoteDiscountUseCase.quote(code, merchandiseSubtotal)` could not express PRODUCT/CATEGORY
coupon scope: eligibility is a per-line decision, and the caller had already collapsed the
cart to a single subtotal. Adding scope forced a port contract change to
`quote(code, List<DiscountLine>)`. **Golden rules:**

1. If a downstream rule needs per-line inputs, pass the lines — a scalar aggregation in the
   caller throws away the information before the rule can see it.
2. Keep the eligibility rule in the domain (`Coupon.isLineEligible`); the caller supplies
   data, the domain decides. Pre-aggregating eligibility in the caller would leak promotion
   logic into the wrong module.
3. Accept that a port signature change ripples to mocks and adapters — that is the cost of
   keeping the rule in the right layer, and unit tests make it cheap.

## 40. A read-modify-write on a shared counter is a race no matter how careful the domain is (2026-08-11)

Coupon `used_count` was incremented by loading the coupon, checking the cap, calling
`recordUsage()` and saving — two concurrent checkouts could both read `used_count == cap - 1`
and both write, exceeding `maxTotalUses`. Domain logic cannot fix this alone. **Golden
rules:**

1. Serialize the read at the persistence seam: `SELECT ... FOR UPDATE` via
   `query.setLockMode(PESSIMISTIC_WRITE)` on a dedicated `findByCodeForUpdate` port method,
   then keep the domain increment under the same transaction.
2. Alternatively use a single atomic statement:
   `UPDATE ... SET used_count = used_count + 1 WHERE used_count < max_total_uses`.
3. Prove it with a concurrent IT (N threads racing against a small cap) — it must yield
   exactly `cap` successes. Mock-based unit tests cannot catch this class of bug.

## 39. Retry on an optimistic-lock conflict must live outside the transactional seam (2026-08-11)

The guest-cart merge (`CartApplicationService.merge`) can hit
`CartConcurrentModificationException` when the user's cart was modified concurrently.
Retrying inside the same `@Transactional` method is pointless: the persistence context is
already marked rollback-only and re-reads the stale version. **Golden rules:**

1. Put the retry loop in the non-transactional caller (the `GuestCartMergeObserver`), where
   each re-invocation of the use case starts a fresh transaction and fresh persistence
   context.
2. Never let a best-effort side effect (cart merge on login) fail the primary flow (login):
   bounded retries, then log and swallow — and always reset the session state.

---

## 38. A confirm modal's no-JSF fallback must submit the form, not re-click the guarded button (2026-08-11)

The admin "Resend" flow: the resend button carries
`onclick="lojaConfirm.show(...); return false;"` to open the modal and prevent the
default submit. On pages without `faces.js` (`jsf` undefined) the modal's Confirm
handler fell back to `el.click()` on that same button — which re-ran
`lojaConfirm.show(...); return false;` and silently cancelled the submit. The form
never posted, so the action never ran (row stayed EXHAUSTED). Fix: submit the real
form via `el.form.requestSubmit(el)` — `requestSubmit` fires the actual submit,
includes the button's `name/value` so the JSF action resolves, and does **not** run
the button's `onclick`. Only fall back to `el.click()` when `requestSubmit` is absent.

Related: the codegpt extension's patchright harness disables inline scripts via CDP
(`Emulation.setScriptExecutionDisabled`), so `<script>` bodies and `onclick` attributes
never execute in it — a modal that "does nothing" under that harness may be fine in a
real browser. Verify web-layer smoke tests in a plain Playwright/real browser.

## 37. A component registered as a Facelets tag must be a Facelets tag, not a JSF composite (2026-08-11)

`confirm-modal.xhtml` was registered in `loja.taglib.xml` as a Facelets tag
(`<tag><source>tags/confirm-modal.xhtml</source></tag>`) but implemented as a JSF
composite (`cc:interface` / `cc:implementation`, `xmlns:cc`). Admin pages that used it
500'd at render. The rewrite to a plain Facelets tag (`<ui:composition>` with EL
attributes and no `cc:` namespace) fixed it. Separately, the inline script used `&&`,
which is an illegal XML character — Facelets parse aborts on the raw `&`; it must be
escaped as `&amp;&amp;`.

**Golden rule:** the taglib `<source>` slot means literal Facelets-tag semantics. If a
page 500s only on pages using a "shared component", check whether it is registered as a
Facelets tag but written as a composite; and in XHTML attribute/script contexts, always
escape `&` as `&amp;` (extends lesson #22's "Facelets is strict XML").

## 36. A @ApplicationScoped scheduled task must be cancelled on @Destroyed, or a hot redeploy leaks a zombie poller (2026-08-11)

`NotificationOutboxDispatcher` started its 5 s fixed-delay poller on
`@Initialized(ApplicationScoped.class)` but never cancelled it. A hot redeploy started a
**second** poller while the previous app instance's task kept running against a destroyed
Weld context, logging `ContextNotActiveException` every 5 s. The fix stores the
`ScheduledFuture` in a volatile field and cancels it (`cancel(false)`) on
`@Destroyed(ApplicationScoped.class)`.

**Golden rule:** any long-lived `ManagedScheduledExecutorService` task started by a CDI
lifecycle observer needs a symmetric stop on the matching `@Destroyed` observer. Symptom
of a leak: a recurring poller that keeps running — and logging context errors — after a
redeploy/stop. `cancel(false)` is enough for a polling task (no runnable "may be running"
concern beyond the next tick).

## 36. JSF `<f:convertDateTime>` cannot format `java.time.Instant` (2026-08-13)

The admin coupon list 500'd with `Cannot format given Object as a Date` for any coupon with a
validity window. `<f:convertDateTime>` accepts `java.util.Date`, `Calendar`, `Long` or a parseable
`String` — **not** `java.time.Instant` (Jakarta Faces 4 / MyFaces). Throwing the domain `Instant`
straight into the Facelet `convertDateTime` is the trap; it fails only when the value is non-null,
so a list of windowless coupons appears fine.

**Golden rule:** surface `Instant` to the Facelet as a pre-formatted `String` from the bean
(e.g. `CouponManagementBean.formatUtc(Instant)` → `"yyyy-MM-dd HH:mm"` with `ZoneOffset.UTC`), and
keep the `!= null` guards in the XHTML. Related: the `04:09` vs `00:09` shift on legacy seeded rows
is the app's JVM `America/New_York` round-trip (see lesson 35) — new coupons written and read by
the app display the exact UTC entered.

## 35. Raw-SQL timestamp inserts must mimic the app's timezone or rows are "in the future" (2026-08-10)

Smoke-testing the Phase D outbox poller, I inserted due rows from psql with bare `now()` into a
`timestamp without time zone` column — the poller (traced every 5s, healthy) never picked them up.
`findDue` compared `next_attempt_at <= :now`, but the app's JVM / DB session timezone is
`America/New_York` and JPA binds `Instant.now()` as a **timestamptz**; Postgres then converts the
stored `timestamp` column to timestamptz **in the session timezone**. So a psql-written UTC
wall-clock value (`01:41`) was interpreted as EDT (`05:41 UTC`) — four hours in the future and
never due. App-written rows are immune because the JDBC driver converts `Instant` to JVM-local
wall-clock on write and read symmetrically.

**Golden rule:** when simulating or backfilling app data in raw SQL, produce timestamps the app's
timezone reads as past/now: `now() AT TIME ZONE 'America/New_York'` (match the JVM default), never
bare `now()`. Symptom: a poller that "seems dead" while `SELECT ... WHERE next_attempt_at <= now()`
clearly matches — check the stored wall-clock vs the app's session timezone before suspecting the
scheduler. (Diagnosed by enabling package trace and seeing `processPending` run every 5 s with an
empty batch.)

## 34. Liberty mail: `@Resource(name="java:app/env/...")` fails; use plain `jndiName` + `@Resource(lookup=...)` (2026-08-10)

Phase D outbox mail: the session was declared as `jndiName="java:app/env/mail/Session"` and injected
with `@Resource(name = "java:app/env/mail/Session")`. At startup Liberty logged
`CWNEN1004E: unable to find the mail/Session default binding with the jakarta.mail.Session type`,
the field stayed null — and the failure was **silent at runtime**: `new MimeMessage(null)` +
`Transport.send(...)` falls back to a default session targeting `localhost:25`, so smokes only ever
showed "Couldn't connect to host, port: localhost, 25", indistinguishable from a real-but-unreachable
server.

**Golden rule:** on Open Liberty use the canonical mail-2.1 pattern —
`<mailSession jndiName="mail/Session" .../>` + `@Resource(lookup = "mail/Session")`. A null Session
that "sort of works" is a red flag: grep the startup log for `CWNEN`/`CWOWB` FFDCs, and make the
from-address come from the session (`session.getProperty("mail.from")`, configured in server.xml) so
a successfully injected session is actually exercised.

## 33. CDI is lazy — an unreferenced @ApplicationScoped bean never runs (2026-08-10)

Phase C's `NotificationOutboxDispatcher` schedules the outbox poll in its constructor hook, but
nothing ever injected the bean, so CDI never instantiated it and the poller silently never ran.
Normal-scoped CDI beans are created on first use, not at deployment (this is not an EJB
`@Singleton @Startup`). Fix without EJB (AGENTS: zero `@EJB`): the dispatcher observes
`@Initialized(ApplicationScoped.class)` —

```java
void schedulePolling(@Observes @Initialized(ApplicationScoped.class) Object event) { ... }
```

— which forces the container to create the bean (and inject `@Resource
ManagedScheduledExecutorService`) during application startup. Verified live: the "Scheduled
notification outbox poll every 5s" INFO line appears at app start; before the fix, nothing did.
Symptom: your "background task" does nothing and there is no log line at all, not even an error.

## 32. EclipseLink's L2 shared cache serves stale rows after direct SQL changes (2026-08-10)

The WAR runs EclipseLink, whose shared (second-level) cache is **on by default** for entities
with no `@Cacheable(false)`. A direct `UPDATE tb_product ...` from a SQL client (ops restock, dev
nudge) is invisible to the running app — the catalog kept rendering "sold out" while the DB row
said `stock = 10`, until a server restart cleared the cache. In-app inventory changes go through
JPQL bulk updates, which EclipseLink does invalidate, so the bug only bites when the database is
edited out-of-band. Symptom: UI state disagrees with `SELECT * FROM tb_product`. Workaround for
dev smoke tests: restart Liberty after any raw-SQL stock mutation. (Only reached because the
smoke had to restock QA Test Widget directly.)

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
