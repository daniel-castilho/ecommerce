# Lessons Learned

Register of subtle bugs and decisions that cost debugging time. Before
implementing something similar, re-read the rules below. New lessons go at the
top, with date and context.

---

## 16. Changing a domain state-machine rule breaks pre-existing transition-matrix tests that assert the old invariant

> Context: v0.6.0 release gate (2026-08-03). Commit `c6800ea` made `ARCHIVED → ACTIVE` legal for
> product reactivation but left `ProductTest.shouldNotTransitionArchivedToDraft` asserting that
> ARCHIVED was terminal (no transitions at all). The module's suite was red at the release gate.

### Symptom

`mvn -pl admin-dashboard -am test` failed in `product-catalog` domain tests at
`ProductTest.shouldNotTransitionArchivedToDraft:167` — `canTransitionTo(ACTIVE)` returned `true`
but the test asserted `false`. The domain and its transition-matrix test disagreed.

### Root cause

The transition map and the transition-matrix test had drifted. The domain map said
`ARCHIVED → {ACTIVE}` (reactivation via `ProductApplicationService.activate()` → `canTransitionTo(ACTIVE)`),
while the test (from the original baseline, where ARCHIVED was terminal) still asserted all three
targets were forbidden.

### Fix applied

```java
// ProductTest — renamed + corrected to match the reactivation rule
void shouldOnlyAllowReactivatingArchivedToActive() {
    // ARCHIVED → DRAFT: false, ARCHIVED → ACTIVE: true, ARCHIVED → INACTIVE: false
}
```

### Golden rules

1. When editing a state-machine rule (e.g. `ALLOWED_TRANSITIONS`), grep the domain tests for
   assertions on the **old** behavior and update them in the same commit — a red suite at release
   time means a transition rule changed without its matrix test.
2. Don't tag a milestone on "committed" alone — run the touched module's fast unit suite
   (`mvn -pl <module> test -Dtest='...'`) before the annotated tag.

### Files involved

- `product-catalog/src/test/java/com/loja/productcatalog/domain/model/ProductTest.java`
- `product-catalog/src/main/java/com/loja/productcatalog/domain/model/Product.java`

---

## 15. Cross-module test classpath uses the *installed* dependency jar — run the consumer with `-am`

> Context: admin-dashboard `RefundManagementBeanTest` (2026-08-03). After adding a bean test
> that imports `RefundRequest` from `order-checkout`, `mvn -pl admin-dashboard test` failed
> with `TestEngine with ID 'junit-jupiter' failed to discover tests`.

### Symptom

Surefire can't discover tests in the consumer module even though the source is fine. The error
is a loading failure of the test class (a `NoClassDefFoundError` for the new sibling-module
class), reported as a discovery failure.

### Root cause

Building a single module (`-pl admin-dashboard`) resolves its `order-checkout` dependency from
the **local Maven repo** (the last `install`ed jar). If that jar predates a class the new test
references, the test class fails to load. The `web` module's CI/jar was re-installed, but
`order-checkout` wasn't.

### Fix applied

Run the consumer module tests in the reactor with `-am` so the dependency is rebuilt from source:

```bash
mvn -pl admin-dashboard -am test -Dtest='RefundManagementBeanTest,RefundDetailBeanTest' -Dsurefire.failIfNoSpecifiedTests=false
```

### Golden rules

1. After adding a class to `order-checkout` (or any dependency module) that a consumer's test
   imports, run consumer tests with `-am`.
2. A module-only `mvn test` that suddenly reports "failed to discover tests" is usually a stale
   installed dependency jar, not a test-framework problem.

### Files involved

- `admin-dashboard/src/test/java/com/loja/admindashboard/adapter/in/web/RefundDetailBeanTest.java`

---

## 14. Deterministic tests on aggregates: use `reconstitute(id, ...)`, not the random-id factory

> Context: refunds list/detail bean tests (2026-08-03). The domain factory
> `RefundRequest.request(...)` assigns `UUID.randomUUID()` as the id.

### Symptom

Bean tests that compare `refund.id` to a fixed string (`loadRefund("r-1")`) failed against
objects built with `request(...)` — every instance has a different random id. Pagination
assertions also drifted: `PageResult.totalPages()` computes from the page/pageSize **stored in
the result**, not from the arguments passed to the list call.

### Root cause / fix

- `request(...)` is the "create new" path (generate a fresh id); `reconstitute(...)` is the
  "restore from persistence" path (fixed id + state). Tests that assert on identity must build
  aggregates with `reconstitute("r-1", ...)`.
- `new PageResult<>(items, total, page, pageSize)` must be constructed with the page you are
  actually asserting (page 0 → `totalPages()==2` for 45 items at 20/page; page 1 → 3), matching
  what the service returns.

### Golden rules

1. Test "create" semantics with `request(...)`; test "find/load/review" semantics with
   `reconstitute(fixedId, ...)`.
2. For pagination assertions, build the expected `PageResult` with the page whose
   `totalPages()` you assert — don't reuse the request's page/pageSize.

### Files involved

- `admin-dashboard/src/test/java/com/loja/admindashboard/adapter/in/web/RefundManagementBeanTest.java`
- `admin-dashboard/src/test/java/com/loja/admindashboard/adapter/in/web/RefundDetailBeanTest.java`

---

## 13. Taglib `<source>` paths are resolved relative to the taglib file, so tag files MUST live in `WEB-INF/tags/`

> Context: admin-dashboard refund detail page (2026-08-03). The confirm modal never appeared at
> runtime — buttons wired to `lojaConfirm.show(...)` did nothing because the JS was never loaded.

### Symptom

The `<my:confirmModal/>` tag rendered nothing and the `window.lojaConfirm` JS was absent from
the page. No compile error; the tag silently produced no markup.

### Root cause

`web/src/main/webapp/tags/confirm-modal.xhtml` sat outside `WEB-INF/tags/`, but
`WEB-INF/loja.taglib.xml` declared `<source>tags/confirm-modal.xhtml</source>`. Facelets
resolves taglib `<source>` paths **relative to the taglib file's directory** (i.e. `WEB-INF/`),
so it looked for `WEB-INF/tags/confirm-modal.xhtml` — which didn't exist. The sibling tags
(`status-badge`, `form-field-group`) were already correctly placed in `WEB-INF/tags/`.

### Fix applied

```bash
git mv web/src/main/webapp/tags/confirm-modal.xhtml web/src/main/webapp/WEB-INF/tags/confirm-modal.xhtml
```

### Golden rules

1. All tag files must live under `WEB-INF/tags/`.
2. A taglib `<source>` is relative to the taglib's own directory, **not** the webapp root —
   `tags/foo.xhtml` means `WEB-INF/tags/foo.xhtml`.
3. A tag that renders nothing at runtime (no error) is usually a resolution problem like this —
   check the tag file's physical location against the taglib entry.

### Files involved

- `web/src/main/webapp/WEB-INF/loja.taglib.xml`
- `web/src/main/webapp/WEB-INF/tags/confirm-modal.xhtml` (moved)

---

## 12. Open Liberty 26.0.0.4+ removed the default LTPA keys password — LTPA dies silently with `CWWKS4118E`

> Context: S2 dashboard smoke test (2026-08-02). A freshly configured Open Liberty
> (26.0.0.7) failed every login with a generic message; the messages log had
> `CWWKS4118E: The LTPA keys file is not initialized` plus `CWWKS4106E: Unable to
> create or read LTPA key file`. No LTPA key was ever created (`ltpa.keys` missing),
> so SSO/token services were effectively down.

### Symptom

`CWWKS4118E` / `CWWKS4106E` in `messages.log`, no `ltpa.keys` file, and container
login (JASPI) failing. On Open Liberty the default LTPA keys password used to be
`WebAS`; it was **removed in Open Liberty 26.0.0.4** as a security fix
(CVE-2025-14917). After that release, if no `keysPassword` is configured, LTPA key
generation fails at startup and the service never comes up.

### Root cause / fix

Two candidate fixes were tried:

1. `keystore_password` in `server.env` — **wrong knob**. It controls the SSL
   key store, not the LTPA keys; the stale `ltpa.keys` (created earlier without a
   password) then failed to be read (`BadPaddingException`). Reverted.
2. Correct fix: declare the LTPA keys password explicitly and regenerate the keys:

```xml
<ltpa keysPassword="keystore-password-dev"/>
```

and delete the old `web/target/liberty/.../ltpa.keys` (created without a password),
then restart. Healthy startup now logs `CWWKS4104A` (keys created) and
`CWWKS4105I` (ready).

### Golden rules

1. Open Liberty >= 26.0.0.4: **always** set `<ltpa keysPassword="..."/>` explicitly;
   do not rely on the removed `WebAS` default.
2. `server.env` `keystore_password` is for the SSL key store — don't use it for LTPA.
3. If `ltpa.keys` was created under the old default, delete it so it is regenerated
   with the new password (in dev; a real cluster would rotate it deliberately).
4. On a `mvn clean` the whole `web/target/liberty` tree (keys, keystore, features)
   is wiped — re-run `./scripts/run-liberty.sh` and re-apply this `server.xml`
   `<ltpa>` block; it lives in `web/src/main/liberty/config/server.xml`.

### Files involved

- `web/src/main/liberty/config/server.xml` (source of truth — `<ltpa keysPassword="keystore-password-dev"/>`)
- `web/src/main/liberty/config/server.env` (unchanged — `keystore_password` reverted)

---

## 11. `HttpServletRequest.login()` is forbidden while a JASPI mechanism is active — use `SecurityContext.authenticate(...)`

> Context: S2 dashboard smoke test (2026-08-02). Every login attempt failed with a generic
> "Invalid email or password", `failed_login_attempts` stayed 0, and no identity-store/JASPI trace
> or SQL appeared — even for a freshly registered user whose password hash the app itself had
> generated. Root cause found by instrumenting `LoginBean` + `LoginAuthenticationMechanism` with
> temporary `System.out` markers.

### Symptom

Valid credentials always render "Invalid email or password". The account counter never moves
(0 failed attempts, `last_login_at` unchanged), which means the identity store never even ran.
A fresh user registered through the app (hash generated by the same adapter) fails identically.
Instrumentation showed:

```
preflight findByEmail present=true canLogin=Optional[true]
calling request.login(...)
request.login THREW ServletException: The login method may not be invoked while JASPI authentication is active.
```

### Root cause

The bean drove login with `HttpServletRequest.login(email, password)` expecting the container to
re-enter `LoginAuthenticationMechanism` with `isAuthenticationRequest() == true`. That recipe is
**wrong for Open Liberty**: with a JASPI `HttpAuthenticationMechanism` registered, Liberty refuses
`request.login()` outright ("The login method may not be invoked while JASPI authentication is
active") — the programmatic-login entry point is `jakarta.security.enterprise.SecurityContext`.
The failure was silent from the user's perspective because `LoginBean` caught the
`ServletException` and rendered the generic message, exactly like a wrong password. This
supersedes the `request.login()` guidance in lessons #8 and #9 (they were written for an
Open Liberty version/path that was never actually exercised end-to-end).

### Fix applied

`LoginBean.login()` now calls `securityContext.authenticate(request, response,
AuthenticationParameters.withParams().credential(new UsernamePasswordCredential(email, password)))`
and maps any status other than `SUCCESS` to the generic message. This re-enters the same
`LoginAuthenticationMechanism` → `IdentityStoreHandler` → `UserIdentityStore` →
`ValidateCredentialsUseCase` path with `isAuthenticationRequest() == true`, so the single-check,
counter-recording, and lockout semantics are unchanged. `@AutoApplySession` persists the caller;
`request.changeSessionId()` stays after success.

### Golden rules

1. On Jakarta EE 11 / Open Liberty, **never** call `HttpServletRequest.login()` for programmatic
   login while an `HttpAuthenticationMechanism` is registered — it throws. Use the injected
   `SecurityContext.authenticate(...)` (CDI `@Inject SecurityContext`, same one `UserBean` already
   used for `isCallerInRole`).
2. Keep the typed credentials in request attributes (the mechanism reads
   `USERNAME_ATTRIBUTE`/`PASSWORD_ATTRIBUTE`) so the mechanism stays implementation-independent;
   `SecurityContext.authenticate` just provides the `isAuthenticationRequest()` trigger.
3. A "wrong password" message with **zero** counter changes is the signature of "the identity
   store never ran" — instrument the bean and the mechanism with one-off log lines before
   chasing the DB/hash (which can be verified standalone).
4. Regression test (`LoginBeanTest`) must mock `SecurityContext.authenticate` returning
   `SUCCESS`/`SEND_FAILURE` and assert `establishSession` runs only on `SUCCESS`.

### Files involved

- `user-account/.../adapter/in/web/LoginBean.java` (`SecurityContext.authenticate` replaces `request.login`)
- `user-account/src/test/java/com/loja/useraccount/adapter/in/web/LoginBeanTest.java`
- `user-account/.../adapter/auth/LoginAuthenticationMechanism.java` (unchanged — already reads request attributes)

---

> Context: `OrderStatus` grew from 3 values to 7 while the status-badge CSS still mirrored the old
> set (2026-08-02). `PROCESSING`, `SHIPPED`, `DELIVERED` and `REFUNDED` rendered with no color;
> the dead `--color-status-open` / `.status-OPEN` lingered for weeks. No test or CI step noticed —
> it breaks nothing functionally, only the visual reinforcement of status.

### Symptom

A `status-badge` for one enum value renders without color while another value that no longer
exists keeps a stale CSS rule.

### Root cause

The design system has a governance doc (`docs/design-system.md` §0, "design-code drift") but no
executable check tying the domain enums (`OrderStatus`, `ProductStatus`, `UserStatus`) to the CSS
that renders them. The enum evolved, the tokens did not follow, and nothing enforced the invariant.

### Golden rules

1. Whenever a Status enum changes, `StatusBadgeCssCoverageTest` (web module) must stay green: it
   checks every enum constant has a `.status-badge.status-<NAME>` rule and a declared
   `--color-status-*` token, and that no rule/token survives without a matching enum constant.
2. New semantic status tokens are Strict tier — reuse existing primitives until the human signs
   off on a new hex value.
3. The web module now has a working test setup (junit-jupiter + assertj + surefire 3.2.5); the
   Maven-bundled surefire 2.12.4 silently reports "Tests run: 0" for JUnit 5 — always pin a modern
   surefire in a module that adds JUnit 5 tests.

### Files involved

- `web/src/test/java/com/loja/web/StatusBadgeCssCoverageTest.java`
- `web/src/main/webapp/resources/css/design-tokens.css`
- `web/src/main/webapp/resources/css/base.css`

---

## 9. When real container auth is layered over a hand-rolled auth path, remove the old path in the same change set

> **SUPERSEDED on the login mechanism, see lesson #11.** The "web layer must call
> `HttpServletRequest.login()`" guidance below is **wrong for Open Liberty**: while a
> JASPI `HttpAuthenticationMechanism` is registered, `request.login()` throws ("The
> login method may not be invoked while JASPI authentication is active"). Use
> `SecurityContext.authenticate(...)`. The rest of this lesson (remove the old
> `LoginUseCase.login()` path; pre-flight `canLogin()`; regression test) still holds.

> Context: `v0.3.0` added real Jakarta Security RBAC (`UserIdentityStore` +
> `LoginAuthenticationMechanism` + `HttpServletRequest.login()`) on top of the pre-existing
> `LoginUseCase.login()` + `SessionPort` path without deleting it (2026-08-02). Every successful
> login then ran the domain check twice: two Argon2id comparisons (deliberately expensive) and two
> separate persists, plus two coexisting sources of truth about "who is logged in"
> (`SessionPort` vs `SecurityContext`).

### Symptom

A successful login takes twice the expected CPU (two Argon2id hashes) and issues two `INSERT`/
`UPDATE` saves for the same event.

### Root cause

Migration added the container path but left `LoginBean` calling `loginUseCase.login()` *and*
`request.login()`. Nothing documented the old call as obsolete, so the duplication looked
intentional.

### Golden rules

1. When the container becomes the credential-verification point, the web layer must call
   `HttpServletRequest.login()` only, and open the app-level session via
   `establishSession()` (no password re-check).
2. Keep a cheap read-only pre-flight (`findByEmail().canLogin()`, no hashing) when you still want
   a precise "account locked/inactive" message without a second Argon2id call — and let unknown
   emails fall through to the generic error so the bean never reveals whether an email is
   registered.
3. Guard the invariant with a regression test (`LoginBeanTest`): assert `request.login()` ran
   exactly once and `LoginUseCase.login()` was never called.

### Files involved

- `user-account/.../adapter/in/web/LoginBean.java`
- `user-account/src/test/java/com/loja/useraccount/adapter/in/web/LoginBeanTest.java`
- `user-account/.../domain/port/in/LoginUseCase.java`

---

## 8. Jakarta Security 4.0 removed `HttpMessageContext.authenticate(Credential)` — programmatic login needs `isAuthenticationRequest()` + `notifyContainerAboutLogin()`

> **SUPERSEDED on the login mechanism, see lesson #11.** The recipe below says the JSF
> bean calls `HttpServletRequest.login(...)` ("still works") — that is **wrong on
> Open Liberty** with a JASPI mechanism active (it throws). Drive login with
> `SecurityContext.authenticate(...)` instead; the rest of the mechanism (inject
> `IdentityStoreHandler`, `notifyContainerAboutLogin(result)`, `@AutoApplySession`,
> request attributes for the typed credentials) is unchanged.

> Context: migrating login/RBAC to real Jakarta Security (2026-08-01). Compilation failed with
> "incompatible types: `Credential` is not a functional interface" — `authenticate(Credential)`
> no longer exists in `jakarta.security.enterprise-api` 4.0 (Jakarta EE 11).

### Symptom

A `LoginAuthenticationMechanism` written against the Jakarta Security 3.x recipe
(`context.authenticate(new UsernamePasswordCredential(...))`) does not compile against
`jakarta.security.enterprise-api` 4.0.

### Root cause

The 4.0 API removed `HttpMessageContext.authenticate(Credential)`. The supported programmatic
flow is:

1. The JSF bean calls `HttpServletRequest.login(email, password)` (still works) — the container
   re-enters the mechanism's `validateRequest` with `context.isAuthenticationRequest()` true.
2. Validate the credential yourself: inject the container-provided `IdentityStoreHandler` and
   call `identityStoreHandler.validate(new UsernamePasswordCredential(u, p))`.
3. On `CredentialValidationResult.Status.VALID`, establish the caller with
   `context.notifyContainerAboutLogin(result)` (carries the `CallerPrincipal` + groups);
   otherwise `context.responseUnauthorized()`.

Other notes from the port:

- `@AutoApplySession` on the mechanism persists the caller in the session.
- Container groups must match the `security-role` names declared in `web.xml`
  (`ADMIN`/`CUSTOMER`/`VENDOR` here) so `@RolesAllowed("ADMIN")` and
  `SecurityContext.isCallerInRole(...)` resolve.
- `Credential` is a plain marker interface in 4.0 (not a functional interface) — a test that
  passed a lambda as a fake credential fails to compile; use `new Credential() { }`.
- Pass the typed credentials to the mechanism via request attributes (not relying on the
  container to re-derive them from `login()`) keeps the flow implementation-independent.
- `request.login()` throws `ServletException` on failure — exactly what the bean catches to
  render the inline `FacesMessage` instead of a container 401.

### Golden rules

1. On Jakarta EE 11 / Security 4.0, never call `context.authenticate(Credential)` — it does not
   exist. Use `isAuthenticationRequest()` + `IdentityStoreHandler.validate(...)` +
   `notifyContainerAboutLogin(...)`.
2. Programmatic login via `HttpServletRequest.login()` still re-enters the mechanism; deliver the
   credentials through request attributes so the mechanism does not depend on container behavior.
3. Container groups = role enum names; keep them aligned with the `security-role` entries in
   `web.xml`.

### Files involved

- `user-account/.../adapter/auth/LoginAuthenticationMechanism.java`
- `user-account/.../adapter/auth/UserIdentityStore.java`
- `user-account/.../adapter/in/web/LoginBean.java`
- `user-account/.../adapter/in/web/UserBean.java` (`SecurityContext`-backed)
- `web/.../WEB-INF/web.xml` (ADMIN security-constraint + security-roles)

---

## 5. Element collection rows losing identity — `Address.id` was never assigned (null id collapses all rows)

> Context: manual QA (2026-07-31) of the address book. "Set as Default" made **every** address
> default; "Remove" removed all addresses at once.

### Symptom

After adding two addresses, clicking "Set as Default" on one of them resulted in **both** rows
having `is_default = true` in `user_address`. Clicking "Remove" removed the whole table.

### Root cause

`UserApplicationService.addAddress(...)` created the address with `new Address(null, ...)` and
**never assigned the id** — every address in the user's collection shared `id = null`. The domain
uses `Objects.equals(a.getId(), addressId)` to find a target (`setDefaultAddress`,
`removeAddress`), so with `addressId = null` **every** address matched:
`Objects.equals(null, null)` is `true` for all of them. `setDefaultAddress` then flipped
`isDefault` on all rows, and `removeAddress(null)` removed all rows.

This is **not** a JPA problem (the `@ElementCollection` of `AddressEmbeddable` — which also lacks
`equals`/`hashCode` — would have its own diff issues on top, but the null-id collapse happened in
pure domain logic first).

### Fix applied

`User.addAddress` now assigns a per-user unique id when none is provided
(`max(existing ids) + 1`, domain-side, no DB dependency — the id is only unique *within* a user's
collection, which is all the domain needs):

```java
private Address withId(Address address) {
    if (address.getId() != null) return address;
    long nextId = addresses.stream().map(Address::getId).filter(Objects::nonNull)
            .mapToLong(Long::longValue).max().orElse(0L) + 1L;
    return new Address(nextId, address.getStreet(), address.getNumber(), ...);
}
```

Test added: `UserTest.shouldAssignUniqueAddressIdsWhenNoneProvided` — the pre-fix behavior
(two addresses, both default after `setDefaultAddress`) fails, post-fix passes.

### Golden rules

1. **Identity-bearing value objects must get their id assigned at creation** — `null` ids in a
   `Set` make every `Objects.equals(null, x)`-based lookup match everything.
2. If the entity has an id column, the **domain must own id assignment** (or receive the id from
   an explicit, tested source). Letting it silently stay `null` is a time bomb for any
   id-targeted operation (`setDefaultAddress`, `removeAddress`, `findById`).
3. For `@ElementCollection` embeddables, give them `equals`/`hashCode` (JPA spec recommendation)
   or the collection diff during `merge()` misbehaves. `AddressEmbeddable` is a known follow-up
   (currently identity-based, acceptable because the collection is fully replaced each save).
4. QA rule that would have caught this immediately: after any per-row operation on a collection,
   assert **exactly one** row changed, not "at least one".

### Files involved

- `user-account/.../domain/model/User.java` (gained `withId` in `addAddress`)
- `user-account/.../domain/model/Address.java`
- `user-account/.../application/service/UserApplicationService.java` (`addAddress` passes null id)
- `user-account/.../domain/model/UserTest.java` (new test)

---

## 4. Testcontainers "Could not find a valid Docker environment" (HTTP 400) on Docker Desktop 29.2.1+ — docker-java API version negotiation

**Date:** 2026-07-31 — during the `order-checkout` IT (`OrderRepositoryJpaAdapterIT`).

### Symptom
`mvn -pl order-checkout -am test` failed immediately in `AbstractIntegrationTest.startContainer`
with `IllegalStateException: Could not find a valid Docker environment`. The testcontainers log showed:

```
UnixSocketClientProviderStrategy: failed with exception BadRequestException (Status 400: {
  "ID":"","Containers":0,"Images":0,...,"ServerVersion":"",
  "Labels":["com.docker.desktop.address=unix:///var/run/docker-cli.sock"],...})
DockerDesktopClientProviderStrategy: failed with exception NullPointerException (getSocketPath() ... null)
```

The confusing part: the **Docker CLI works fine** (`docker ps`, `docker info` → Server 29.2.1,
`curl --unix-socket /var/run/docker.sock .../_ping` → `OK`, `/info` → 200), and the **product-catalog
ITs pass with the exact same testcontainers 1.21.3**.

### Root cause
Docker Desktop (Docker Engine **29.2.1+**, min API version **1.44**) dropped support for old API
versions. The `docker-java` client bundled with testcontainers **1.21.3** defaults to negotiating a
much older API version (1.30/1.32); the daemon (via Docker Desktop's WSL socket proxy, see the
`com.docker.desktop.address` label) answers the `/info` probe with **HTTP 400** → no strategy
validates → `IllegalStateException`. Confirmed upstream: testcontainers-java issue #11491 and the
Docker forum (the fix in 1.x requires **≥ 1.21.4**).

Why only `order-checkout` failed: `product-catalog`'s surefire config already passes
`<argLine>-Dapi.version=1.44</argLine>` (added for LocalStack) — `docker-java` reads that system
property as the API version to negotiate. `order-checkout` lacked it. Identical library, one module
green, the other red.

### Fix applied
Add the same `argLine` to `order-checkout`'s `maven-surefire-plugin` configuration:

```xml
<argLine>-Dapi.version=1.44</argLine>
```

(Machine-level alternatives: `api.version=1.44` in `~/.docker-java.properties`, or upgrade
testcontainers to ≥ 1.21.4. The pom `argLine` keeps it project-local and consistent with
`product-catalog`.)

### Golden rules
1. Any module with Testcontainers ITs running against Docker Desktop 29.2.1+ needs the docker-java
   API version pinned: `<argLine>-Dapi.version=1.44</argLine>` in surefire. Keep all modules consistent.
2. When one module's ITs pass but an identical-looking module fails with "Could not find a valid
   Docker environment" + HTTP 400, **diff the surefire `<argLine>`/system properties first** — the
   docker client stack is the same, so the difference is in the fork's config.
3. To see which strategies Testcontainers tries, add `slf4j-simple` **1.7.36** as a test dependency
   (matches the `slf4j-api 1.7.36` that testcontainers pulls; `2.0.x` will not bind). Without a
   binding the strategy logs are invisible.
4. An empty `/info` JSON body with a `com.docker.desktop.address` label + 400 is the Docker Desktop
   **socket proxy**, not a broken daemon — `curl`/`docker` against the same socket returning 200
   means: don't chase Docker, fix the client's API version.

### Files involved
- `order-checkout/pom.xml` (maven-surefire-plugin `<argLine>-Dapi.version=1.44</argLine>`)
- `product-catalog/pom.xml` (same `argLine`, pre-existing)
- `order-checkout/src/test/java/com/loja/ordercheckout/adapter/out/persistence/AbstractIntegrationTest.java`

---

## 3. IT suite got stuck at the `TRUNCATE` in the `setUp()` — `idle in transaction` connection blocked the exclusive lock

**Date:** 2026-07-31 — during Step 3 of the `product-catalog` (`decrementStock` + concurrency test S9).

### Symptom
`mvn -pl product-catalog test` **hung** (>10 min) at the `TRUNCATE TABLE ...` of
the `@BeforeEach` of `ProductRepositoryJpaAdapterIT`. The log only pointed to a
leaked connection: `Connection leak detected: there are 1 unclosed connections upon
shutting down pool`.

### Root cause
Two things combined:

1. **Reads outside a transaction + pool with `autoCommit=false`.** The test harness
   called `adapter.findById/search/existsBySku/...` **without** an explicit transaction
   (the application server provides `@Transactional`; the test does not). With Hibernate in
   `autoCommit=false`, the connection stays open in an **implicit transaction** — `pg_stat_activity`
   showed the previous test's pid as `idle in transaction`,
   holding `AccessShareLock` on `tb_product(_image,_category)`.
2. **`em` never closed.** The `AbstractIntegrationTest` creates `em` but has no
   `@AfterEach` to close it → the connection with the open transaction leaks back to
   the pool (the aforementioned "1 unclosed connection").

Result: the next test's `TRUNCATE` (which requires `AccessExclusiveLock`) stays
blocked **forever** by the previous test's `AccessShareLock`.

### What does NOT work (tried)
`hibernate.connection.autocommit=true`: the deadlock went away, but it broke
(a) `@Lob` reading (`Unable to access lob stream` — needs a transaction) and
(b) the concurrency test S9 (result `[1, 1]` instead of `[1, 0]` — the race
lost its atomic semantics). **Don't disable the pool's autoCommit to "fix" a test.**

### Fix applied
- `@AfterEach` in `ProductRepositoryJpaAdapterIT` closes the `em`
  (`if (em != null && em.isOpen()) em.close();`) — returns the connection with no pending transaction.
- Every adapter read in the IT now runs in an explicit transaction via the helper
  `inTx(() -> ...)` (`begin`/`commit`/`rollback`/`clear`) — mirrors the `@Transactional`
  of production.

### Bonus found along the way (paginating)
`ProductRepositoryAdapter.search()` used `getResultStream()`, which **ignores**
`setFirstResult`/`setMaxResults` (Hibernate logs `HHH90003004: firstResult/maxResults
specified with collection fetch; applying in memory`). Page 2 returned 20 items
instead of 5. Switching to `getResultList()` fixed it.

### Golden rule
1. In ITs with Hibernate `RESOURCE_LOCAL`, **every adapter operation needs an
   explicit transaction** (or a helper that begins/commits it), just like the `@Transactional`
   of production.
2. **Always close the `EntityManager`** in the `@AfterEach` — the pool's "Connection leak detected"
   is the symptom; the deadlock on `TRUNCATE`/DDL is the consequence.
3. If a test hangs on `TRUNCATE`/`DROP`, investigate with
   `SELECT pid, state, wait_event_type, query FROM pg_stat_activity` and `pg_locks`:
   look for `idle in transaction` holding `AccessShareLock` against `AccessExclusiveLock`.
4. `getResultStream()` + `setFirstResult`/`setMaxResults` is a Hibernate gotcha:
   use `getResultList()`.

### Files involved
- `product-catalog/src/test/java/com/loja/productcatalog/adapter/out/persistence/ProductRepositoryJpaAdapterIT.java`
- `product-catalog/src/test/resources/META-INF/persistence.xml`
- `product-catalog/src/main/java/com/loja/productcatalog/adapter/out/persistence/ProductRepositoryAdapter.java`

---

## 2. `mvn test` in `product-catalog` is slow — Testcontainers boots a Postgres per run

**Date:** 2026-07-31 — during Step 2 of the `product-catalog` (the session aborted the command twice).

### Symptom
`mvn -pl product-catalog test` takes dozens of seconds. It's not lack of CPU: it's the integration
test. Running the whole suite just to validate the compilation of new ports is
a waste of time.

### Root cause
`AbstractIntegrationTest` uses `new PostgreSQLContainer<>("postgres:15").start()` **without**
`.withReuse(true)` — on every run Maven boots a new container, initializes
Hibernate/EMF and tears it down at the end. The `ProductRepositoryJpaAdapterIT` (17 tests) runs
exactly like that.

### Fast feedback strategy (adopted)
- Module compilation: `mvn -q -pl product-catalog test-compile` (seconds).
- Unit tests without container: `mvn -pl product-catalog test -Dtest='ProductTest,SkuTest,SlugTest,ProductJpaMapperTest' -DfailIfNoTests=false`.
- Whole-project compilation (validates that `order-checkout` still compiles against the ports):
  `mvn -q -pl order-checkout -am compile`.
- Run the `*IT.java` only when validating the persistence layer (Step 3/5).

### Golden rule
1. Before every `mvn test`, ask "do I need the real DB right now?". If not, filter with
   `-Dtest=...` excluding `*IT`.
2. If speed becomes a real priority: `.withReuse(true)` + `testcontainers.reuse.enabled=true`
   in `~/.testcontainers.properties` keeps the container alive between runs.

### Files involved
- `product-catalog/src/test/java/com/loja/productcatalog/adapter/out/persistence/AbstractIntegrationTest.java`

---

## 1. `em.merge()` of a detached entity with `@Version = null` + child `@OneToMany` → duplicate INSERT (Hibernate)

**Date:** 2026-07-30 — found in S2 of the `product-catalog` (test `shouldUpdateProductInPlace`).

### Symptom
When calling `repository.save(product)` a second time (update), Hibernate
did a **correct UPDATE of the row** and then a **duplicate INSERT**, blowing up with
`duplicate key value violates unique constraint ..._pkey`. The error even arrived misleadingly
as `DuplicateSkuException` (because of the detection heuristic).

### Root cause
The graph has a child `@OneToMany(cascade = ALL)` (`ProductImageJpaEntity`) with a
back-reference `@ManyToOne` to the parent. During `merge`, the cascade to the child makes Hibernate
**re-merge the parent** through that association. Since the parent instance is
**detached with a null `@Version`**, the second merge can't treat it as
detached and schedules an `INSERT`.

`user-account` never suffered this because `UserJpaEntity` only has embedded collections
(`@ElementCollection`) — no child entity with a back-reference to the parent.

### Fix applied
The `@Version` (and, by extension, the optimistic locking state) now **round-trips
through the domain**:
- The `Product` domain gained `version` (getter/setter);
- `ProductJpaMapper.toJpa` sets `entity.setVersion(product.getVersion())`;
- `ProductJpaMapper.toDomain` restores `product.setVersion(entity.getVersion())`;
- `ProductRepositoryAdapter.save()` keeps `em.merge(...)` + `em.flush()` and returns
  the domain rebuilt from the managed copy (the flush also **materializes the generated
  ids** of `ProductImageJpaEntity` and the `version` on the returned object).

### Golden rule
1. **Entity with a child entity that references the parent: the detached object passed to
   `merge()` must carry the `@Version`.** Otherwise Hibernate can insert
   instead of update. The same holds for any graph the cascade traverses.
2. The `@Version` must be carried between domain ↔ JPA whenever the entity
   has `@OneToMany`/`@ManyToMany` with cascade — the round-trip in the mapper is what
   makes the `OptimisticLockException` translatable at the service layer.
3. If one day you replace `merge()` with "load the managed entity and apply state"
   (`em.find` + dirty checking), this trap disappears — it's the plan B if the
   version round-trip becomes unfeasible.
4. Debugging "duplicate insert on update": enable `hibernate.show_sql` and confirm whether
   the sequence is `update` followed by `insert` on the same table — the signature of this bug.
5. **The rule is broader than "collection with cascade":** it applies to **any entity
   with `@Version` + generated id** that goes detached through `merge()`. In Step 4
   (`CategoryJpaEntity`, which only has `@ManyToOne parent`, no `@OneToMany`), the merge
   of a detached category with `version=null` blew up with
   `PropertyValueException: Detached entity with generated id '1' has an uninitialized
   version value 'null'`. The domain `Category` gained `version` with a round-trip in the
   `CategoryJpaMapper`, just like `Product`.

### Files involved
- `product-catalog/.../adapter/out/persistence/ProductJpaMapper.java`
- `product-catalog/.../adapter/out/persistence/ProductRepositoryAdapter.java`
- `product-catalog/.../adapter/out/persistence/AuditableJpaEntity.java` (gained `setVersion`)
- `product-catalog/.../domain/model/Product.java` (gained `version`)
- `product-catalog/.../domain/model/Category.java` + `CategoryJpaMapper.java` (Step 4 case)

---

## 7. ArchUnit `ports_should_be_interfaces` counts nested records — DTOs live in `application.dto`; make concurrent ITs deterministic

> Context: S10/S12 of the order epic (2026-08-01). Two ArchUnit failures from the same trap,
> plus two concurrency tests that needed a specific harness.

### Symptom / root cause 1 — records nested in a port

`ProductHexagonalArchitectureTest.ports_should_be_interfaces` failed with
`com.loja.productcatalog.domain.port.out.InventoryReservationPort$ReservationRequest` reported
as a violation. ArchUnit treats a `record` nested inside an interface as a **class** in
`domain.port`, so "everything in `..domain.port..` must be an interface" breaks.
`CreateOrderFromCartUseCase` had the same problem (`CheckoutCommand`, `ItemCheckoutRequest`
nested in `port/in`) — it only surfaced when the order module got its own ArchUnit test (S12).

**Fix:** DTO records that travel through a port live in `application/dto`, never nested in the
port (`ReservationRequest`, `CheckoutCommand`, `ItemCheckoutRequest` moved). The `domain`
allowed-dependencies whitelist already includes `application.dto`, so ports referencing them
stay architecture-clean.

**Golden rule 1:** under ArchUnit `ports_should_be_interfaces`, any nested `record` in a port
is a violation. Keep command/query DTOs in `application/dto` from day one.

### Root cause 2 — the concurrent-checkout IT was flaky-by-design

Two new concurrency tests (S11) needed to be deterministic, not "sometimes green":

1. **Two checkouts, same cart, one must win.** Both workers run the real `OrderRepositoryAdapter`
   against the shared Postgres, each with its **own** `EntityManager` (`emf.createEntityManager()` —
   a single shared `em` is not thread-safe). A `CyclicBarrier(2)` installed via
   `doAnswer(...)` on the mocked `InventoryReservationPort.reserve` guarantees both threads are
   past the `findById` (empty) check before either commits, so the loser reliably hits the PK
   constraint at `save`.
2. **Two status updates, one must win.** The `@Version` round-trip makes the loser throw
   `OptimisticLockException`, wrapped by the adapter as `OrderConcurrentModificationException`.
   Barrier placed **inside** the transaction (after `findById`, before `save`) so both hold the
   same version when they race.

**Assertion trap:** `verify(inventoryReservation).confirm("e2e-race")` with `times(1)` failed —
**both** workers legitimately call `confirm` before `save` (the mock records both; the real
reservation is idempotent by `reservationId`). The deterministic assertion is the **DB row
count** (`SELECT COUNT(*) ...`), not the mock call count.

**Golden rules:**
1. Concurrent ITs: one `EntityManager` per worker thread; a `CyclicBarrier` inside a mocked
   port call (or inside the transaction) makes the race deterministic.
2. Assert the durable effect (row counts, versions, stock) rather than how many times a mock
   was invoked — idempotent real-world operations are invoked by every contender.
3. `@Version` on a JPA entity must round-trip through the domain mapper (lesson #1) for
   `OptimisticLockException` to surface reliably on `merge`+`flush`.

### Files involved

- `product-catalog/.../application/dto/ReservationRequest.java` (moved out of the port)
- `order-checkout/.../application/dto/CheckoutCommand.java`, `ItemCheckoutRequest.java`
- `order-checkout/.../domain/model/Order.java`, `OrderJpaEntity.java` (`version` round-trip)
- `order-checkout/.../adapter/out/persistence/OrderRepositoryAdapter.java` (`OptimisticLockException` → `OrderConcurrentModificationException`)
- `order-checkout/.../OrderHexagonalArchitectureTest.java`
- `order-checkout/src/test/.../OrderApplicationServiceIT.java`, `OrderRepositoryJpaAdapterIT.java`

---

## 6. `mvn clean package` wipes the Open Liberty install under `web/target`

### Symptom
After a clean build + `mvn -pl web liberty:start`, the app never comes up on 9443:
- `curl` to `https://localhost:9443` hangs or returns `000`;
- `messages.log` shows `CWWKF0001E: A feature definition could not be found for
  webprofile-11.0` and later `CWPKI0819I: The default keystore is not created because
  a password is not configured ... and the 'keystore_password' environment variable is
  not set` (the HTTPS port stays down, only 9080 listens).

### Root cause
`web/target/liberty` is a **build artifact**: `mvn clean` deletes it, along with the
installed features (`wlp/lib/features`) and the auto-generated `key.p12` keystore. A fresh
`liberty:start` therefore boots the bare `openliberty-kernel` with zero features and no
keystore. On top of that, the previous server JVM keeps running from the now-deleted files
and still owns ports 9080/9443, so the new start can't bind.

### Fix applied
- `web/src/main/liberty/config/server.xml` declares a dev-only
  `<keyStore id="defaultKeyStore" password="keystore-password-dev"/>` so the keystore
  auto-generates on start (dev credential only; the datasource connection is externalized via
  `${env.DB_*}` with dev defaults in `server.env`, see the README datasource note).
- `scripts/run-liberty.sh` makes a fresh install reproducible: `mvn package` (no `clean`,
  keeps an existing install), and if the `webProfile-11.0` feature manifest is missing it
  runs `mvn -pl web liberty:create` + `featureUtility installServerFeatures defaultServer
  --acceptLicense` (one-time download), then starts the server and copies
  `web/target/web.war` into `dropins/`.

### Golden rule
1. Never assume `web/target/liberty` survives a build. After `mvn clean package`, run
   `./scripts/run-liberty.sh` instead of `mvn liberty:start`.
2. If the old server still holds the ports (a killed `clean` under it), kill the leftover
   `ws-server.jar defaultServer` processes before starting a fresh one.
3. The WAR is deployed as `dropins/web.war` (auto-deploy); a plain `mvn liberty:start`
   does **not** deploy the app. Prefer `scripts/run-liberty.sh` over the individual goals.
