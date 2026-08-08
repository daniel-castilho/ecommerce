# User Account — Implementation Sequence (As-Built)

**Companion docs:** `user-account-module-spec.md` · `user-account-backlog.md`

This document records the **actual delivery order** of the user-account work.
Detailed pre-build step plans (legacy split-module layouts) are preserved in git history of this file.

> **Epic S1–S17 completed 2026-07-30** in the single `user-account` module.
> **Jakarta Security RBAC** landed as **v0.3.0** (2026-08-01).

---

## Guiding principles used

- Single Maven module: **`user-account`** (`com.loja.useraccount`)
- UI pages in **`web`**
- Domain framework-free; application may use CDI / `@Transactional`
- Argon2id for passwords; lockout after failed attempts
- Events + observers for audit and email side-effects
- ArchUnit as the hexagonal safety net

---

## Actual delivery sequence

### Phase 1 — Core epic (Steps 0–17)

**Completed: 2026-07-30**

| Step  | What landed                                                                     |
| ----- | ------------------------------------------------------------------------------- |
| 0     | Module wiring, Flyway user schema (`V5__user_account_schema.sql`)               |
| 1     | Domain: `User`, roles, addresses, lockout / reset token behaviour               |
| 2     | Ports in/out + DTOs                                                             |
| 3–6   | JPA adapter, search/pagination, Argon2id hasher, session adapter                |
| 7–8   | Notification + audit adapters (observers)                                       |
| 9     | `UserApplicationService` (register, login, profile, password, admin list/roles) |
| 10–15 | UI: login, register, profile, address book, password change, password reset     |
| 16    | Admin users list + role assignment (`@RolesAllowed("ADMIN")`)                   |
| 17    | `UserHexagonalArchitectureTest`                                                 |

**Verified at completion:** large unit + IT suite green; reactor build SUCCESS.

---

### Phase 2 — Container RBAC

**Release: v0.3.0 (2026-08-01)**

1. `UserIdentityStore` backed by credential validation use case
2. `LoginAuthenticationMechanism` (Jakarta Security 4.0 flow + `@AutoApplySession`)
3. `LoginBean` / `HttpServletRequest.login()` establishes container identity
4. `UserBean` reads `SecurityContext` for `isLoggedIn` / `hasRole`
5. `web.xml` ADMIN constraints for admin and manage-product paths
6. Session-role guards kept as belt-and-braces where useful

See `docs/lessons.md` (Security 4.0 API notes) and `docs/releases/v0.3.0.md`.

---

### Phase 3 — Later integrations

| Item              | Notes                                                                     |
| ----------------- | ------------------------------------------------------------------------- |
| Dashboard metrics | `CountUsersUseCase` / repository `count()` for admin KPIs                 |
| Block / unblock   | Status change use case + domain events → audit observer                   |
| Bean Validation   | On **adapter** JSF beans only (domain stays free of `jakarta.validation`) |

---

## Notable implementation facts

- Password reset: 24h token; events on request and successful change
- Admin list: deterministic sort by email; pagination
- ArchUnit: domain depends only on shared-kernel + `java.*`; ports are interfaces
- Early Flyway: manual `flyway_schema_history` registration pattern (V5 era)

---

## Recommended order for any _new_ work

1. Change identity rules only in domain + pure unit tests
2. Keep security mechanism / IdentityStore in adapters
3. Expose metrics or lookups via ports for other modules
4. Never put plaintext passwords in logs or entities
5. After auth UI changes, smoke register → login → protected admin page

---

## Useful commands

```bash
mvn -pl user-account test -Dtest='*Test' -DfailIfNoTests=false
mvn -pl user-account test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false
mvn clean package -pl web -am
./scripts/run-liberty.sh
```

Smoke path:

1. Register → login → profile / address book
2. Forgot password → reset confirm
3. Admin users list (ADMIN role) → assign role / block

---

## Definition of Done (sequence)

- [x] Full account lifecycle + admin user management
- [x] ArchUnit + tests
- [x] Container Jakarta Security RBAC
- [x] Metrics and status-change hooks for admin dashboard

---

_This is the as-built execution record. For older step-by-step plans and session notes, see the git history of this file._

```

```
