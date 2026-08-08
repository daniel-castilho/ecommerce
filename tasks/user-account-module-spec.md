# User Account Module — Technical Specification (As-Built)

**Status:** Living document reflecting the implemented **reference** module (epic delivered 2026-07-30; Jakarta Security in **v0.3.0**).
**Companion docs:** `user-account-backlog.md` · `user-account-implementation-sequence.md`

The original long-form pre-implementation specification is preserved in git history of this file.

---

## 1. Purpose & Architectural Role

`user-account` owns identity: registration, authentication, profile/addresses, password flows, roles, and audit/notification side-effects.

It is **cross-cutting**: other modules consume its **ports** (session, current user, counts, status change). It does **not** depend on catalog or order domains.

---

## 2. Package Layout (actual)

Single Maven module **`user-account`**, package root `com.loja.useraccount`:

```
domain/
  model/ + events/ + exception/
  port/in/   → register, login, profile, address, password, admin list/roles, metrics, …
  port/out/  → UserRepositoryPort, PasswordHasherPort, SessionPort, DomainEventPublisherPort, …
application/
  service/   → UserApplicationService (and focused services as needed)
  dto/
adapter/
  in/web/        → Register, Login, Profile, AddressBook, PasswordReset, AdminUsers, UserBean
  in/security/   → UserIdentityStore, LoginAuthenticationMechanism (v0.3.0)
  out/           → JPA, Argon2 hasher, session, notification/audit observers
  session/       → @CurrentUser producer
```

UI pages under `web/src/main/webapp/user-account/` (and shared admin paths).

**ArchUnit:** domain free of `jakarta.*`/`javax.*`; ports are interfaces; adapters implement ports.

---

## 3. Domain Model (as implemented)

### User aggregate

| Aspect    | Choice                                                                                               |
| --------- | ---------------------------------------------------------------------------------------------------- |
| Id        | **String UUID**                                                                                      |
| Email     | Value object **`Email`** (normalized)                                                                |
| Profile   | first/last or full-name helpers; phone; preferences                                                  |
| Password  | Hash only via hasher port; Argon2id in adapter                                                       |
| Roles     | **CUSTOMER** (default), **ADMIN**, **VENDOR** — stored as rows in `user_role` (no ROLE lookup table) |
| Status    | ACTIVE / INACTIVE / LOCKED                                                                           |
| Addresses | Separate table keyed by user; one default                                                            |
| Reset     | Token + expiry (24h) columns                                                                         |

**Behaviour:** authenticate with failure counting → lockout; change/reset password; address default rules; soft inactive; role checks.

### Events (published by application service)

Examples: `UserRegisteredEvent`, `UserLoggedInEvent`, `PasswordChangedEvent`, `PasswordResetRequestedEvent`, address events, block/unblock events (later).

**Observers** (not direct port calls from the service for email/audit): welcome email, password-reset email, audit log.

---

## 4. Application Layer

`UserApplicationService` (and related use-case implementations) orchestrate:

- Register / login / logout
- Profile + address book
- Password change + forgot/reset
- Admin list, filters, pagination, assign role
- Status change (block/unblock)
- Metrics (`count`, etc.) for admin-dashboard

Outbound ports: repository, password hasher, session, domain event publisher (notification/audit via observers).

---

## 5. Persistence

- Flyway **`V5__user_account_schema.sql`** (and later additive migrations as needed)
- Flattened `UserJpaEntity` + addresses + role rows
- Search via Criteria (email, status, pagination; deterministic order by email for admin)

---

## 6. Security (as built)

| Phase      | Mechanism                                                                                                                                       |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Early epic | Session + `@RolesAllowed` + template guards                                                                                                     |
| **v0.3.0** | Jakarta Security: `IdentityStore` + custom `AuthenticationMechanism`, `request.login()`, `SecurityContext` in `UserBean`, `web.xml` constraints |

Session cookies / JSF ViewState provide CSRF protection for forms.
Password hashing: **`PasswordHasherArgon2Adapter`**.

---

## 7. Web / UI

| Page                     | Role                              |
| ------------------------ | --------------------------------- |
| login / register         | Public auth                       |
| profile / address-book   | Authenticated                     |
| password-reset + confirm | Public token flow                 |
| admin/users              | ADMIN — list, filter, assign role |

`UserBean` (`@SessionScoped`) exposes `loggedIn`, `hasRole`, current user to EL.

---

## 8. What Differs From the Original Spec

| Original aspiration                                      | As built                               |
| -------------------------------------------------------- | -------------------------------------- |
| Split `user-domain` / `catalog-adapters` / `catalog-web` | Single `user-account` + pages in `web` |
| Long IDs + ROLE lookup table                             | String UUID; roles as VARCHAR rows     |
| Ports under `application/port`                           | Ports under `domain/port`              |
| Service calls Notification/Audit ports directly          | Domain events + CDI observers          |
| Session-only RBAC forever                                | Evolved to container Jakarta Security  |

---

## 9. Explicit Debt

- Real SMTP/SendGrid (often mock)
- MFA, OAuth/social login, JWT API tokens
- Permission matrix finer than role names

---

## 10. Testing

- Domain pure unit tests
- Application with mocked ports
- Adapter ITs (Testcontainers)
- `UserHexagonalArchitectureTest`
- Security unit tests for IdentityStore (v0.3.0+)

---

## 11. Definition of Done (module)

- [x] Full account lifecycle + admin user management
- [x] Argon2id + lockout + audit/email observers
- [x] Hexagonal boundaries
- [x] Container RBAC (Jakarta Security)
- [x] Ports for other modules (session, counts, status)

---

_This document describes the module as implemented. For the original pre-build design and open questions, see the git history of this file._

```

```
