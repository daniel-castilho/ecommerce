# User Account — Backlog Status

**Companion documents:**
`user-account-module-spec.md` · `user-account-implementation-sequence.md`

**Epic goal:** Registration, login, profile/address management, password flows, and RBAC so other modules can personalize behaviour and gate admin features.

> **Epic S1–S17 delivered** (2026-07-30).
> **Real Jakarta Security RBAC** shipped as **v0.3.0** (2026-08-01).
> Later: metrics ports for admin dashboard, block/unblock + audit events (admin-dashboard era).

---

## Current Status Summary

| Story / area                             | Status  | Notes                                                    |
| ---------------------------------------- | ------- | -------------------------------------------------------- |
| S1 Domain model                          | ✅ Done | `User`, roles, addresses, lockout rules; pure unit tests |
| S2–S3 Persistence + search               | ✅ Done | JPA adapter, Criteria search, pagination                 |
| S4 Argon2id hashing                      | ✅ Done | Never stores plaintext                                   |
| S5 Session / current user                | ✅ Done | Session port + `@CurrentUser` + `UserBean` for EL        |
| S6–S10 Register / login / logout / audit | ✅ Done | Events + observers for audit / welcome                   |
| S11 Profile                              | ✅ Done | Name, phone, preferences                                 |
| S12 Address book                         | ✅ Done | CRUD + default address                                   |
| S13–S14 Password change / reset          | ✅ Done | 24h reset token flow                                     |
| S15–S16 Admin roles + user list          | ✅ Done | Role assignment, admin user list                         |
| S17 RBAC enforcement                     | ✅ Done | Evolved to container Jakarta Security (v0.3.0)           |
| Count / metrics for dashboard            | ✅ Done | `CountUsersUseCase` etc.                                 |
| Block / unblock                          | ✅ Done | Status change + audit events (admin composition)         |

---

## Implemented (original S1–S17)

### Foundation

- Framework-free domain; status ACTIVE / INACTIVE / LOCKED
- Argon2id password hashing
- JPA persistence + searchable admin list

### Auth & session

- Register → default CUSTOMER role
- Login with lockout after repeated failures
- Logout + session invalidation
- `@CurrentUser` producer and `#{userBean}` for templates

### Account management

- Profile update
- Address book with default
- Password change (authenticated)
- Forgot-password reset with expiring token

### Admin & security

- Admin user list + role assignment
- Audit logging of sensitive actions (observers)
- Welcome / reset emails via notification port (mock or configured)
- **v0.3.0:** `UserIdentityStore` + `LoginAuthenticationMechanism` + `HttpServletRequest.login()`; `web.xml` constraints; `SecurityContext`-backed role checks

### Architecture

- `UserHexagonalArchitectureTest` green
- Large unit + IT suite (reference module for the monolith)

---

## Later additions

| Feature                          | Notes                                                                     |
| -------------------------------- | ------------------------------------------------------------------------- |
| Dashboard metrics                | `count()` / growth helpers consumed by admin-dashboard                    |
| Block / unblock                  | `ChangeUserStatusUseCase` + domain events → audit                         |
| Bean Validation on adapter beans | Constraints on JSF beans only (domain stays free of `jakarta.validation`) |

---

## Explicit debt / optional next work

- Real email provider (still often mock — see notification guide)
- Fine-grained permissions beyond CUSTOMER / ADMIN / VENDOR roles
- MFA / social login (never in original epic)

---

## How the module is structured today

```
com.loja.useraccount/
├── domain/model + port/in + port/out + exception
├── application/service + application/dto
└── adapter/
    ├── in/web/          → Register, Login, Profile, AddressBook, AdminUsers, …
    ├── in/security/     → IdentityStore + AuthenticationMechanism (v0.3.0)
    └── out/             → JPA, password hasher, session, notification/audit adapters
```

Other modules consume **ports only** (e.g. session, count users). Admin customer screens may live under admin-dashboard as composition.

---

## Definition of Done (Epic)

- [x] Register / login / logout
- [x] Profile + address book + password flows
- [x] Admin user list + roles
- [x] Argon2id + lockout + audit
- [x] Container RBAC (Jakarta Security)
- [x] ArchUnit + tests

---

_This backlog is a living status document. For the original INVEST stories (Given/When/Then, story points, full DoR/DoD), see the git history of this file._

```

```
