# User Account Module — Agile Backlog Refinement

**Companion to:** `user-account-module-spec.md` (technical design) and `user-account-implementation-sequence.md` (session completion record).

**Purpose of this document:** Break down the User Account module into independently valuable, shippable stories with Given/When/Then acceptance criteria, Definition of Ready, and Definition of Done — enabling incremental delivery and concurrent work if needed.

> ## ✅ EPIC DELIVERED (July 30, 2026)
>
> All 17 stories below are **implemented and verified** in the `user-account` module (hexagonal, package root `com.loja.useraccount`; UI pages in the `web` module). The body reflects the original planning assumptions (module names, `UserEmail`, `@CurrentUser`-only, ROLE lookup table); for the authoritative as-built facts see the "IMPLEMENTED" box at the top of `user-account-module-spec.md`.
>
> **Epic-level Definition of Done — all met:** register/login/logout work; current user context available via `@CurrentUser` **and** `UserBean` (`#{userBean.hasRole(...)}`); RBAC gates admin features (`@RolesAllowed("ADMIN")` on `AdminUsersBean`); audit logging + welcome/reset emails via CDI observers; passwords Argon2id-hashed; session timeout + account lockout (5 failed attempts → LOCKED) implemented; `UserHexagonalArchitectureTest` (ArchUnit) green; password reset (24h token) and admin user list/role assignment delivered.
>
> **Test suite:** `mvn test -pl user-account` → 117 tests green (108 unit + 9 IT, incl. Testcontainers ITs). Root reactor build → all modules SUCCESS.

---

## Epic: User Authentication & Account Management

**Epic goal:** Enable user registration, login, profile management, and role-based access control. Provide identity context to all other modules so checkout, order history, and wishlist are personalized per user.

**Epic-level Definition of Done:** All stories below are done; users can register and log in; current user context is available to all other modules via @CurrentUser injection; RBAC gates admin features; audit logging is complete; no credentials stored in plain text; session timeout and account lockout work; ArchUnit hexagonal-boundary test passes.

---

## Story Map (Dependency Order)

```
S1 Domain model ──▶ S2 JPA persistence ──▶ S3 Repository port & search
                                               │
S4 Password hashing (Argon2id) ─────────────────┤
                                               │
S5 Session management & @CurrentUser ──────────┤
                                               ▼
S6 User registration ────────▶ S7 Send welcome email (notification)
                               │
                               ▼
S8 User login ────────▶ S9 Audit logging (login attempts)
                       │
                       ▼
S10 Logout ◀─────────────┘

S11 Profile management (update name, phone, preferences)
S12 Address book (CRUD + set default)
S13 Password change
S14 Password reset (forgot password flow)
S15 Role assignment (admin only)
S16 User list (admin only)
S17 RBAC enforcement (@RolesAllowed, JSF permission checks)
```

Each story is sized to be completable and demoable independently, but dependencies (arrows) are hard constraints.

---

### S1 — Domain Model for User

**Status:** ✅ Delivered (UserTest green, zero framework imports in domain)

**As** the system, **I want** a framework-free `User` domain object with value objects for email, password, profile, and address, **so that** all higher stories build on a domain that cannot represent invalid states.

**Priority:** Must (MoSCoW)

**Definition of Ready:**
- [ ] Spec §3 (domain model) reviewed and approved
- [ ] Password storage strategy confirmed: Argon2id hashing (plaintext never stored)
- [ ] Session strategy confirmed: HttpSession-based (@CurrentUser injection)
- [ ] RBAC scope confirmed: CUSTOMER + ADMIN roles for MVP

**Acceptance Criteria:**

- **Given** a new User in ACTIVE status, **when** `canLogin()` is called, **then** it returns true.
- **Given** a User with failed login attempts = 5, **when** `canLogin()` is called, **then** it returns false and status should be LOCKED.
- **Given** a User with a valid password, **when** `authenticate(password)` is called, **then** it returns true.
- **Given** a User with an invalid password, **when** `authenticate(password)` is called, **then** it returns false and `failedLoginAttempts` increments.
- **Given** a User with no addresses, **when** `addAddress(address, setAsDefault=true)` is called, **then** the address is added and marked as default.
- **Given** a User with 2 addresses where address1 is default, **when** `setDefaultAddress(address2)` is called, **then** address1 is unmarked and address2 becomes default.
- **Given** a User with roles [CUSTOMER], **when** `hasRole(ADMIN)` is called, **then** it returns false.
- **Given** a valid email "user@example.com", **when** `UserEmail` is constructed, **then** it succeeds and normalizes to lowercase.
- **Given** an invalid email "invalid-email", **when** `UserEmail` is constructed, **then** it throws IllegalArgumentException.
- **Given** a plaintext password "mypassword", **when** `UserPassword.hash()` is called, **then** it returns a hash (not plaintext, uses Argon2id).

**Definition of Done:**
- `UserTest` covers all acceptance criteria and passes with zero mocks
- Value objects (UserEmail, UserPassword, UserProfile, Address) are immutable
- All domain classes compile with zero framework imports
- Domain exceptions defined and used correctly

**Story Points:** 8

---

### S2 — JPA Persistence Adapter for User

**Status:** ✅ Delivered (UserJpaMapperTest + UserRepositoryJpaAdapterTest/IT green, V5 migration applied)

**As** the system, **I want** `UserJpaEntity` + `UserJpaMapper` + `UserRepositoryJpaAdapter`, **so that** the domain `User` can be persisted without the domain layer knowing JPA exists.

**Depends on:** S1

**Definition of Ready:**
- [ ] S1 merged
- [ ] Table schema finalized (§6 of spec) — confirm column names, types, nullability
- [ ] M:N join table for USER_ROLE confirmed
- [ ] Flyway migration tool confirmed (already in project)

**Acceptance Criteria:**

- **Given** a valid `User` domain object, **when** `UserRepositoryJpaAdapter.save()` is called, **then** a row is persisted in `USER_ACCOUNT` table and the returned `User` has a non-null id.
- **Given** a persisted user with 2 addresses, **when** `findById()` is called, **then** the User is loaded with both addresses intact.
- **Given** a persisted user with roles [CUSTOMER, ADMIN], **when** `findById()` is called, **then** both roles are loaded.
- **Given** a user email "test@example.com", **when** `findByEmail("test@example.com")` is called, **then** the user is returned.
- **Given** a user email "nonexistent@example.com", **when** `findByEmail()` is called, **then** Optional.empty() is returned.
- **Given** 50 users with mixed status (ACTIVE, INACTIVE, LOCKED), **when** `findAll(page=0, size=20)` is called, **then** 20 results are returned and only ACTIVE users are included (inactive filtered out).
- **Given** a persisted user with status ACTIVE, **when** the user is updated to INACTIVE, **then** the update is persisted correctly.
- **Given** an order for password reset, **when** the user is loaded, **then** `passwordResetToken` and `passwordResetTokenExpiresAt` are preserved.

**Definition of Done:**
- `UserJpaMapperTest` passes (domain ↔ JPA round-trip)
- `UserRepositoryJpaAdapterTest` (Testcontainers, real DB) passes for CRUD + search cases
- Migration `V5__user_account_schema.sql` applied successfully against local DB
- No class outside `adapter.persistence` imports `UserJpaEntity`
- Column indexes verified (email, user_id on addresses, user_id on audit log)

**Story Points:** 5

---

### S3 — Repository Port & Search for User

**Status:** ✅ Delivered (findAll with email/status criteria + pagination via CriteriaStrategy)

**As an** admin, **I want** to query users by status, email, and date range with pagination, **so that** I can manage user accounts efficiently.

**Depends on:** S2

**Definition of Ready:**
- [ ] S2 merged
- [ ] Default/max page size confirmed (20/100)

**Acceptance Criteria:**

- **Given** 100 users in the DB, **when** `findAll(page=0, pageSize=20)` is called, **then** 20 results are returned with totalPages=5.
- **Given** a mix of users with status ACTIVE, INACTIVE, LOCKED, **when** `findAll(criteria with status=ACTIVE)` is called, **then** only ACTIVE users are returned.
- **Given** 50 ACTIVE users, **when** `findAll(page=0)` is called with default sort, **then** results are sorted by createdAt DESC (newest first).
- **Given** a user with email "john@example.com", **when** `findByEmail("john@example.com")` is called, **then** the correct user is returned (case-insensitive search).
- **Given** 100 users created over a month, **when** `findAll(criteria with fromDate=today-7days)` is called, **then** only users created in the last 7 days are returned.

**Definition of Done:**
- `UserRepositoryJpaAdapterTest` covers all search/filter combinations
- Queries verified to use indexes (check `EXPLAIN PLAN` in PR description)
- No N+1 on addresses or roles (JOIN FETCH or @EntityGraph used)

**Story Points:** 3

---

### S4 — Password Hashing (Argon2id)

**Status:** ✅ Delivered (PasswordHasherArgon2Adapter + tests)

**As** the system, **I want** passwords hashed with Argon2id, **so that** even if the database is compromised, passwords cannot be reversed.

**Depends on:** S1

**Definition of Ready:**
- [ ] Argon2id library (e.g., `org.bouncycastle:bcprov-jdk15on` or `de.mkammerer:argon2-jvm`) added to pom.xml
- [ ] Hash configuration parameters confirmed (time cost, memory cost, parallelism)

**Acceptance Criteria:**

- **Given** plaintext password "mySecurePassword123", **when** `PasswordHasherArgon2Adapter.hash()` is called, **then** it returns a hash that is NOT the plaintext (Argon2id format: $argon2id$...).
- **Given** a plaintext password and its hash, **when** `verify(password, hash)` is called with correct password, **then** it returns true.
- **Given** a plaintext password and its hash, **when** `verify(password, hash)` is called with wrong password, **then** it returns false.
- **Given** two calls to `hash()` with the same password, **when** both results are compared, **then** they are different (due to different salts).
- **Given** an Argon2id hash from one call, **when** `verify()` is called with the correct password later, **then** it still returns true (hash is consistent).
- **Given** a weak password < 8 characters, **when** `UserPassword.hash()` is called, **then** it throws IllegalArgumentException.

**Definition of Done:**
- `PasswordHasherArgon2AdapterTest` passes all verification scenarios
- No plaintext password ever stored in UserJpaEntity
- Hashing is never done in domain constructor, only via UserPassword.hash() factory method
- Configuration (time cost, memory) is externalized (not hardcoded)

**Story Points:** 3

---

### S5 — Session Management & @CurrentUser Injection

**Status:** ✅ Delivered (SessionAdapter + @CurrentUser producer + UserBean for templates)

**As** a JSF bean, **I want** to inject `@CurrentUser User` and get the logged-in user, **so that** I don't have to manually fetch from HttpSession.

**Depends on:** S1, S4

**Definition of Ready:**
- [ ] SessionPort interface defined
- [ ] @CurrentUser qualifier defined
- [ ] CDI producer pattern confirmed

**Acceptance Criteria:**

- **Given** a logged-in user, **when** a JSF bean injects `@CurrentUser User`, **then** the current user is resolved from the session.
- **Given** no logged-in user, **when** a JSF bean injects `@CurrentUser User`, **then** CDI throws SessionExpiredException (or similar).
- **Given** a user session is created, **when** the session timeout expires (30 min), **then** subsequent requests find no session.
- **Given** a logged-in user, **when** `SessionPort.invalidateSession()` is called (logout), **then** the HttpSession is destroyed and subsequent requests have no user.
- **Given** a user logs in, **when** session is created, **then** session cookies are set with Secure, HttpOnly, SameSite=Strict flags.

**Definition of Done:**
- `SessionAdapterTest` passes (create, read, invalidate)
- @CurrentUser producer is @ApplicationScoped (singleton)
- SessionExpiredException or proper error handling for unauthenticated access
- Session timeout configured and testable
- CSRF tokens (JSF ViewState) included in all forms

**Story Points:** 3

---

### S6 — User Registration

**Status:** ✅ Delivered (RegisterUserUseCase + RegisterBean + register.xhtml, UserRegisteredEvent)

**As a** new user, **I want** to sign up with email and password, **so that** I can create an account.

**Depends on:** S2, S3, S4, S5

**Definition of Ready:**
- [ ] All adapter stories (S2–S5) merged
- [ ] Welcome email template ready (or mock)

**Acceptance Criteria:**

- **Given** a new registration with email "john@example.com" and password "SecurePass123", **when** `RegisterUserUseCase.registerUser()` is called, **then** a User is created with status ACTIVE, role CUSTOMER, and persisted.
- **Given** an existing user with email "john@example.com", **when** a new registration attempts the same email, **then** `EmailAlreadyRegisteredException` is thrown.
- **Given** a new registration, **when** the user is persisted, **then** the email is unique in the database (unique constraint enforced).
- **Given** a successful registration, **when** it completes, **then** a welcome email is sent to the user (mocked in tests).
- **Given** a weak password < 8 characters, **when** registration is attempted, **then** `IllegalArgumentException` is thrown (password validation in domain).
- **Given** an invalid email format, **when** registration is attempted, **then** `IllegalArgumentException` is thrown (email validation in UserEmail value object).
- **Given** a successful registration, **when** the user logs in immediately after, **then** they can authenticate with the password they registered.

**Definition of Done:**
- `RegisterBeanTest` (integration test with mocked ports) passes
- User is created with CUSTOMER role by default
- Email is stored in lowercase (normalized)
- Password is hashed before storage (never plaintext in DB)
- Welcome email notification sent (or logged in mock)
- Audit log entry created (REGISTRATION event)
- JSF register.xhtml page works (form, validation messages)

**Story Points:** 5

---

### S7 — Send Welcome Email (Notification Integration)

**Status:** ✅ Delivered (WelcomeEmailObserver → NotificationPort/NotificationEmailAdapter)

**As** the system, **I want** to send a welcome email after successful registration, **so that** the user knows their account is active.

**Depends on:** S6

**Definition of Ready:**
- [ ] S6 merged
- [ ] NotificationPort interface defined
- [ ] Email template finalized (or placeholder)

**Acceptance Criteria:**

- **Given** a successful user registration, **when** `NotificationPort.sendWelcomeEmail()` is called, **then** an email is queued/sent.
- **Given** a mock email adapter, **when** `sendWelcomeEmail()` is called, **then** the email is logged (not actually sent in dev).
- **Given** an email send fails, **when** registration completes, **then** registration is NOT rolled back (email is non-blocking, async-able).
- **Given** a user with email "john@example.com" and name "John Doe", **when** they receive the welcome email, **then** the email includes their name and account confirmation.

**Definition of Done:**
- `NotificationMockAdapterTest` passes
- Email template (if real adapter) is externalized (not hardcoded Java strings)
- Welcome email is sent asynchronously (non-blocking to registration)
- Email failures are logged but don't fail registration

**Story Points:** 2

---

### S8 — User Login

**Status:** ✅ Delivered (LoginUseCase + LoginBean + login.xhtml, lockout at 5 failures)

**As a** registered user, **I want** to log in with email and password, **so that** I can access my account.

**Depends on:** S2, S3, S4, S5

**Definition of Ready:**
- [ ] Session management (S5) merged
- [ ] Password hashing (S4) merged
- [ ] Audit logging ready

**Acceptance Criteria:**

- **Given** a registered user with email "john@example.com" and password "SecurePass123", **when** login is attempted with correct credentials, **then** the user is authenticated and a session is created.
- **Given** a registered user, **when** login is attempted with wrong password, **then** login fails and `InvalidPasswordException` is thrown.
- **Given** a registered user with wrong email, **when** login is attempted, **then** login fails and `InvalidPasswordException` is thrown (no info leak about email existence).
- **Given** a user with status LOCKED (5+ failed logins), **when** login is attempted, **then** login fails without password check.
- **Given** a user with status INACTIVE (soft-deleted), **when** login is attempted, **then** login fails.
- **Given** a successful login, **when** the session is checked, **then** the user is stored in HttpSession.
- **Given** 4 failed login attempts on a user, **when** the 5th attempt fails, **then** the user status becomes LOCKED.
- **Given** a successful login after previous failed attempts, **when** login completes, **then** `failedLoginAttempts` is reset to 0.
- **Given** a successful login, **when** the user's `lastLoginAt` field is checked, **then** it is updated to the current timestamp.

**Definition of Done:**
- `LoginBeanTest` (integration test with mocked ports) passes
- Session is created after successful login (verified via SessionPort.createSession)
- Failed attempts counter increments on each failure
- Account lockout after 5 failures works
- Account cannot log in while LOCKED
- Audit log entries created for success/failure
- JSF login.xhtml page works (form, error messages, redirect to dashboard on success)
- No password echo/leak in error messages

**Story Points:** 5

---

### S9 — Audit Logging (Login Attempts & Events)

**Status:** ✅ Delivered (AuditLogObserver → AuditLogPort/AuditLogJpaAdapter)

**As** an admin, **I want** to see audit logs of all login attempts (success/failure), **so that** I can detect suspicious activity.

**Depends on:** S8

**Definition of Ready:**
- [ ] S8 merged
- [ ] AuditLogPort interface defined
- [ ] USER_AUDIT_LOG table schema ready

**Acceptance Criteria:**

- **Given** a successful login, **when** the audit log is queried, **then** a LOGIN_SUCCESS event is recorded with timestamp, user ID, and event details.
- **Given** 3 failed login attempts, **when** the audit log is queried, **then** 3 separate LOGIN_FAILED events are recorded.
- **Given** a password change, **when** the audit log is queried, **then** a PASSWORD_CHANGED event is recorded.
- **Given** a role assignment by admin, **when** the audit log is queried, **then** a ROLE_ASSIGNED event is recorded with which admin and which role.
- **Given** an audit log entry, **when** it is queried, **then** it includes: timestamp, user ID, event type, IP address (if available), user agent, and details.

**Definition of Done:**
- `AuditLogJpaAdapterTest` (Testcontainers) passes
- Audit log entries are immutable (no updates, only inserts)
- Events are logged for: LOGIN_SUCCESS, LOGIN_FAILED, PASSWORD_CHANGED, ROLE_ASSIGNED, REGISTRATION, LOGOUT
- IP address and user agent are captured from HTTP request (if available)

**Story Points:** 3

---

### S10 — Logout

**Status:** ✅ Delivered (LogoutUseCase invalidates session)

**As** a logged-in user, **I want** to log out, **so that** my session is invalidated and someone else can't use my account.

**Depends on:** S8

**Definition of Ready:**
- [ ] S8 merged
- [ ] Session invalidation verified

**Acceptance Criteria:**

- **Given** a logged-in user, **when** `LogoutUseCase.logout()` is called, **then** the HttpSession is invalidated.
- **Given** a logged-out user, **when** they try to access a protected page, **then** they are redirected to login.
- **Given** a successful logout, **when** the audit log is checked, **then** a LOGOUT event is recorded.
- **Given** a logout, **when** the session cookies are checked, **then** the session cookie is cleared (Set-Cookie with Max-Age=0).

**Definition of Done:**
- `LogoutBeanTest` passes
- Session is destroyed (not just the user attribute)
- Logout link/button works on JSF pages
- Audit log entry created
- User is redirected to login page after logout

**Story Points:** 2

---

### S11 — Profile Management (Update Name, Phone, Preferences)

**Status:** ✅ Delivered (UpdateProfileUseCase + ProfileBean + profile.xhtml)

**As a** logged-in user, **I want** to update my profile (name, phone, language, notification preferences), **so that** my account reflects my current information.

**Depends on:** S8 (logged-in context)

**Definition of Ready:**
- [ ] S8 merged
- [ ] UpdateProfileUseCase defined

**Acceptance Criteria:**

- **Given** a logged-in user, **when** they update their first and last name, **then** the profile is persisted and subsequent reads show the new name.
- **Given** a logged-in user, **when** they update their phone number, **then** it is persisted correctly.
- **Given** a logged-in user, **when** they change their preferred language to "en_US", **then** subsequent UI interactions use English (future: i18n support).
- **Given** a logged-in user, **when** they toggle notification preferences OFF, **then** no emails are sent for subsequent events (future implementation).
- **Given** a profile update, **when** the user is reloaded, **then** all changes are persisted.
- **Given** an invalid first name (empty string), **when** profile update is attempted, **then** `IllegalArgumentException` is thrown.

**Definition of Done:**
- `ProfileBeanTest` passes
- JSF profile.xhtml page works (form bindings, submit, success message)
- Updates are persisted to DB
- Audit log entry created (PROFILE_UPDATED event)
- Error handling for validation failures

**Story Points:** 3

---

### S12 — Address Book (CRUD + Set Default)

**Status:** ✅ Delivered (address use cases + AddressBookBean + address-book.xhtml)

**As a** logged-in user, **I want** to manage multiple addresses (add, edit, delete, set default), **so that** I can specify different shipping addresses for orders.

**Depends on:** S8 (logged-in context)

**Definition of Ready:**
- [ ] S8 merged
- [ ] AddAddressUseCase, UpdateAddressUseCase, DeleteAddressUseCase, SetDefaultAddressUseCase defined

**Acceptance Criteria:**

- **Given** a logged-in user, **when** they add a new address, **then** the address is persisted and linked to their account.
- **Given** a logged-in user with 2 addresses, **when** they view their address book, **then** all 2 addresses are displayed.
- **Given** a logged-in user with an existing address, **when** they edit it (change street/city/etc.), **then** the update is persisted.
- **Given** a logged-in user with 2 addresses where address1 is default, **when** they add a new address with setAsDefault=true, **then** address2 becomes default.
- **Given** a logged-in user with 2 addresses, **when** they try to delete the default address but it's the only one, **then** deletion is prevented (at least one address required).
- **Given** a logged-in user with an address, **when** the address is set as default, **then** subsequent checkouts show this address by default.
- **Given** an invalid CEP format, **when** address is added, **then** `IllegalArgumentException` is thrown.

**Definition of Done:**
- `AddressBookBeanTest` passes
- JSF address-book.xhtml page works (list, add form, edit form, delete confirmation)
- Addresses are persisted to USER_ADDRESS table
- One address is always marked default (enforced at domain level)
- Checkout UI can query default address via repository

**Story Points:** 5

---

### S13 — Password Change

**Status:** ✅ Delivered (ChangePasswordUseCase + PasswordChangedEvent)

**As a** logged-in user, **I want** to change my password, **so that** I can update it if I think it's compromised.

**Depends on:** S8, S4

**Definition of Ready:**
- [ ] S8 merged
- [ ] ChangePasswordUseCase defined

**Acceptance Criteria:**

- **Given** a logged-in user with password "OldPass123", **when** they change it to "NewPass456" (providing old password for verification), **then** the password is updated in the database.
- **Given** a logged-in user, **when** they provide the wrong current password, **then** change is rejected with `InvalidPasswordException`.
- **Given** a password change, **when** the audit log is checked, **then** a PASSWORD_CHANGED event is recorded.
- **Given** a password change, **when** the user tries to log in with the old password, **then** login fails.
- **Given** a password change, **when** the user logs in with the new password, **then** login succeeds.
- **Given** a weak new password (< 8 characters), **when** change is attempted, **then** `IllegalArgumentException` is thrown.

**Definition of Done:**
- `ChangePasswordBeanTest` passes
- JSF page for password change works (current password, new password, confirm new password)
- Current password is verified before allowing change
- New password is hashed (Argon2id) before storage
- Audit log entry created

**Story Points:** 3

---

### S14 — Password Reset (Forgot Password Flow)

**Status:** ✅ Delivered (PasswordResetBean + password-reset.xhtml + password-reset-confirm.xhtml, 24h UUID token, PasswordResetRequestedEvent/PasswordChangedEvent, findByResetToken)

**As a** user who forgot their password, **I want** to reset it via email token, **so that** I can regain access without contacting support.

**Depends on:** S2, S4, S7 (email notification)

**Definition of Ready:**
- [ ] S7 (notifications) merged
- [ ] RequestPasswordResetUseCase, ResetPasswordUseCase defined
- [ ] Email template for password reset ready (or placeholder)

**Acceptance Criteria:**

- **Given** a user who forgot their password, **when** they request a password reset with their email, **then** a reset token is generated and an email is sent to them.
- **Given** a reset token, **when** it is checked, **then** it is valid for 24 hours (expiry enforced).
- **Given** an expired reset token, **when** password reset is attempted, **then** request is rejected.
- **Given** a valid reset token in the URL (e.g., /reset?token=abc123), **when** user submits a new password, **then** the password is updated and token is invalidated.
- **Given** multiple reset requests for the same email, **when** each generates a new token, **then** only the latest token is valid (previous tokens are overwritten).
- **Given** a successful password reset, **when** the user logs in with the new password, **then** login succeeds.
- **Given** a password reset, **when** the audit log is checked, **then** a PASSWORD_RESET event is recorded.

**Definition of Done:**
- `PasswordResetBeanTest` passes
- JSF pages: password-reset.xhtml (email form) and password-reset-confirm.xhtml (new password form with token validation)
- Reset token is generated securely (e.g., UUID + timestamp signed)
- Token expiry enforced (24 hours)
- Email sent with reset link (includes token in URL)
- New password is hashed before storage
- Audit log entries created

**Story Points:** 5

---

### S15 — Role Assignment (Admin Only)

**Status:** ✅ Delivered (AssignRoleUseCase + AdminUsersBean.assignRole)

**As an** admin, **I want** to assign roles to users (CUSTOMER, ADMIN, VENDOR), **so that** I can control access and permissions.

**Depends on:** S2, S3, S8

**Definition of Ready:**
- [ ] S8 merged
- [ ] AssignRoleUseCase defined
- [ ] @RolesAllowed("ADMIN") enforcement ready

**Acceptance Criteria:**

- **Given** a non-admin user, **when** they try to assign a role via use case, **then** `InsufficientPermissionException` is thrown.
- **Given** an admin user, **when** they assign the ADMIN role to another user, **then** the role is added to that user's roles set.
- **Given** a user with role CUSTOMER, **when** an admin assigns ADMIN role, **then** the user now has both CUSTOMER and ADMIN.
- **Given** a role assignment, **when** the user's `hasRole(ADMIN)` is checked, **then** it returns true.
- **Given** a role assignment, **when** the audit log is checked, **then** a ROLE_ASSIGNED event is recorded with which admin assigned which role.
- **Given** a user with an assigned role, **when** they access an admin-only page (decorated with @RolesAllowed("ADMIN")), **then** access is granted.

**Definition of Done:**
- `AdminUsersTest` passes (admin operations)
- @RolesAllowed("ADMIN") enforced on AssignRoleUseCase method
- Roles are loaded eagerly (FetchType.EAGER on @ManyToMany) so hasRole() is instant
- Audit log entry created with details

**Story Points:** 3

---

### S16 — User List (Admin Only)

**Status:** ✅ Delivered (ListUsersUseCase + AdminUsersBean paginated list, admin/users.xhtml)

**As an** admin, **I want** to view a paginated list of all users (with filters), **so that** I can manage accounts.

**Depends on:** S2, S3, S15 (admin-only check)

**Definition of Ready:**
- [ ] S15 merged
- [ ] ListUsersUseCase defined
- [ ] Admin dashboard structure ready

**Acceptance Criteria:**

- **Given** an admin user, **when** they access the admin users page, **then** they see a paginated list of all users.
- **Given** a list of 100 users, **when** admin views page 1 (20 per page), **then** 20 users are shown with totalPages=5.
- **Given** admin filtering by status=ACTIVE, **when** the list is filtered, **then** only ACTIVE users are shown (INACTIVE and LOCKED filtered out).
- **Given** admin searching by email, **when** they search "john", **then** users with email containing "john" are shown.
- **Given** an admin viewing the user list, **when** they click on a user, **then** user details are shown (email, name, roles, last login, addresses).
- **Given** an admin viewing a user, **when** they see action buttons, **then** options to edit roles, reset password, or deactivate are available.

**Definition of Done:**
- `AdminUsersTest` passes
- JSF admin/users.xhtml page works (list with pagination, filters, detail view)
- Only ADMIN role can access admin pages (enforced by @RolesAllowed or CheckUserRoleUseCase)
- Queries use indexes for performance
- Soft-deleted (INACTIVE) users are shown in admin view but not to public

**Story Points:** 3

---

### S17 — RBAC Enforcement (@RolesAllowed, JSF Permission Checks)

**Status:** ✅ Delivered (@RolesAllowed("ADMIN") on AdminUsersBean + #{userBean.hasRole('ADMIN')} guards)

**As** the system, **I want** to enforce role-based access control throughout the application, **so that** non-admin users cannot access admin features.

**Depends on:** S15 (role assignment exists)

**Definition of Ready:**
- [ ] S15 merged
- [ ] @RolesAllowed annotations ready
- [ ] JSF permission helper methods ready

**Acceptance Criteria:**

- **Given** an application service method decorated with `@RolesAllowed("ADMIN")`, **when** a CUSTOMER user calls it, **then** `AccessDeniedException` is thrown by the container.
- **Given** an admin-only JSF page (admin/users.xhtml), **when** a non-admin tries to access it, **then** they are redirected to an error page (401 Unauthorized or redirect to home).
- **Given** a JSF page with `rendered="#{userBean.hasRole('ADMIN')}"`, **when** a CUSTOMER views the page, **then** the admin-only section is not rendered.
- **Given** an admin-only button, **when** a CUSTOMER tries to access it directly (if rendered incorrectly), **then** the backend method throws AccessDeniedException.
- **Given** a CUSTOMER user accessing a CUSTOMER-only feature (e.g., my orders), **when** they pass their own user ID, **then** access is granted.
- **Given** a CUSTOMER user accessing another user's orders, **when** they pass a different user ID, **then** access is denied (data isolation enforced).

**Definition of Done:**
- `RBACTest` passes (permission checks)
- @RolesAllowed annotations applied to all admin-only methods
- JSF pages have `rendered` attributes for permission-based UI
- UserBean exposes `hasRole()` method for template use
- Tests verify both positive (authorized) and negative (denied) scenarios
- No permission bypass possible (backend enforces, not just frontend)

**Story Points:** 3

---

## Backlog Summary Table

| # | Story | Depends on | Priority | Points |
|---|---|---|---|---|
| S1 | Domain model | — | Must | 8 |
| S2 | JPA persistence adapter | S1 | Must | 5 |
| S3 | Repository search | S2 | Must | 3 |
| S4 | Password hashing (Argon2id) | S1 | Must | 3 |
| S5 | Session management & @CurrentUser | S1, S4 | Must | 3 |
| S6 | User registration | S2, S3, S4, S5 | Must | 5 |
| S7 | Welcome email notification | S6 | Should | 2 |
| S8 | User login | S2, S3, S4, S5 | Must | 5 |
| S9 | Audit logging | S8 | Should | 3 |
| S10 | Logout | S8 | Must | 2 |
| S11 | Profile management | S8 | Should | 3 |
| S12 | Address book (CRUD) | S8 | Must | 5 |
| S13 | Password change | S8, S4 | Should | 3 |
| S14 | Password reset (forgot pwd) | S2, S4, S7 | Should | 5 |
| S15 | Role assignment (admin) | S2, S3, S8 | Should | 3 |
| S16 | User list (admin) | S2, S3, S15 | Should | 3 |
| S17 | RBAC enforcement | S15 | Must | 3 |

**Total:** 70 points. For a solo developer, this is a 5–6 week epic (assuming ~12–15 points per week velocity).

**Sequencing:** 
- **Foundation (S1–S5):** 22 points, 1.5–2 weeks. Internal milestone (domain + adapters, not yet user-facing).
- **Authentication (S6–S10):** 17 points, 1–1.5 weeks. User-facing: registration, login, logout. Core epic value.
- **Account Management (S11–S17):** 25 points, 2–2.5 weeks. Profile, addresses, admin, RBAC. Completion.

**Note on "Should" items (S7, S9, S11, S13, S14, S15, S16):** Several are marked "Should" because they don't block core epic value (register + login works without them), but all are strongly recommended for MVP. Defer only if timeline is tight; they are high-priority enhancements, not nice-to-have.
