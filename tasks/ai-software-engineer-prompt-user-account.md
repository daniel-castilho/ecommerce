# AI Software Engineer Prompt — User Account Module Implementation

> ## ⚠️ SUPERSEDED (July 30, 2026)
>
> The User Account module is **already implemented and verified** — do **not** execute this prompt as-is. It assumes a repo layout (`user-domain/`, `catalog-adapters/`, `catalog-web/`, Open Liberty) that does **not** exist. The real module is `user-account/` (package root `com.loja.useraccount`) with UI pages in `web/`; see the "IMPLEMENTED" box at the top of `user-account-module-spec.md` for the authoritative as-built description, and `user-account-implementation-sequence.md` (Session Completion Record) for the verified state. Kept for historical reference.

**Context:** You are an AI Software Engineer tasked with implementing the User Account module for a Jakarta EE e-commerce project that follows hexagonal architecture and uses Open Liberty as the application server. The project has already successfully completed the Product Catalog and Order & Checkout modules, which serve as reference implementations for all architectural patterns and conventions.

**Your primary responsibility:** Implement the complete User Account module following the specifications, backlog, and implementation sequence provided. The goal is to enable user registration, login, profile management, and role-based access control (RBAC) — providing identity context to all other modules.

**Working directory:** The repository root contains:
- `user-domain/` — domain models, application ports, application services (NEW MODULE YOU CREATE)
- `catalog-adapters/` — JPA entities, persistence adapters, password hashing, session, notification, audit log adapters (EXTEND)
- `catalog-web/` — JSF managed beans, XHTML pages (EXTEND)
- `pom.xml` — parent Maven POM
- `docs/` — specifications, action plan, progress tracking
- `tasks/` — backlog, implementation sequence, coding standards

**Reference documents (READ THESE FIRST):**
1. `user-account-module-spec.md` — Complete technical design (what to build)
2. `user-account-backlog.md` — 17 INVEST stories with acceptance criteria (why, in what order)
3. `user-account-implementation-sequence.md` — 17 step-by-step build instructions (exactly how, no guessing)
4. `order-checkout-module-spec.md` — Reference for hexagonal patterns (same as User Account)
5. `java-jakarta-ee-coding-standards.md` — Code style and patterns (enforced, zero deviations)

**Architecture mandate (non-negotiable):**
- ✅ **Open Liberty only** — zero GlassFish, zero Tomcat
- ✅ **Jakarta EE 10/11** — 100% `jakarta.*` imports, zero `javax.*`
- ✅ **Hexagonal architecture** — domain/application/adapters layering, dependency rule (inward only)
- ✅ **Repository ports** — zero DAO pattern, all persistence behind port interfaces
- ✅ **CDI only** — zero @EJB, @Stateless (use `@ApplicationScoped + @Transactional` instead)
- ✅ **Session-based auth (Phase 1)** — HttpSession + RBAC, JWT deferred to Phase 2
- ✅ **Password security** — Argon2id hashing, never store plaintext

**Standards enforced:** every file you create must comply with `java-jakarta-ee-coding-standards.md`. This is not optional. Every deviation is a bug.

---

## Pre-Implementation Checklist (Do This First)

Before writing any code, verify these prerequisites with the human:

- [ ] **Argon2id library chosen:** de.mkammerer:argon2-jvm or org.bouncycastle:bcprov-jdk15on?
  - Confirm dependency added to `catalog-adapters/pom.xml`
  
- [ ] **Session configuration confirmed:** Open Liberty server.xml has session management with:
  - HttpOnly cookies: true
  - Secure cookies: true
  - SameSite: Strict
  - Max inactive interval: 30 minutes
  
- [ ] **Jakarta Mail available:** for password reset emails (or mock-only for dev)

- [ ] **RBAC scope confirmed:** 2 roles for MVP (CUSTOMER, ADMIN). VENDOR defined but not used yet.

- [ ] **Password requirements confirmed:** Minimum 8 characters, no complexity rules (can add later)

**If any of the above is unclear, STOP and ask the human before proceeding. Do not guess or improvise.**

---

## Implementation Order (Follow Strictly)

You have **17 steps** to follow in **exact order**. Do not skip or reorder.

### STEP 0 — Environment & Configuration

**Read:** `user-account-implementation-sequence.md`, Step 0

**Actions:**
1. Add Argon2id library to `catalog-adapters/pom.xml`:
   ```xml
   <dependency>
     <groupId>de.mkammerer</groupId>
     <artifactId>argon2-jvm</artifactId>
     <version>2.11</version>
   </dependency>
   ```

2. Verify Jakarta Mail available (or mock):
   ```xml
   <dependency>
     <groupId>jakarta.mail</groupId>
     <artifactId>jakarta.mail-api</artifactId>
     <version>2.0.1</version>
   </dependency>
   ```

3. Confirm `mvn dependency:resolve` succeeds for all new SDKs

**Done when:**
- [ ] All SDKs compile
- [ ] No dependency conflicts
- [ ] Session configuration reviewed in Open Liberty config

---

### STEP 1 — Create User Domain Module

**Read:** `user-account-implementation-sequence.md`, Step 1 + `user-account-module-spec.md`, Section 3

**Create Maven module structure:**
```bash
mkdir -p user-domain/src/main/java/.../user/domain
mkdir -p user-domain/src/main/java/.../user/application
mkdir -p user-domain/src/test/java/.../user/domain
```

**Create value objects in `user-domain/src/main/java/.../user/domain/`:**

1. **`UserEmail.java`** — immutable, validates email format (per spec §3.2)
   - Constructor normalizes to lowercase, trims whitespace
   - Validates RFC 5322 simplified format
   - Throws IllegalArgumentException if invalid
   - Implements equals/hashCode

2. **`UserPassword.java`** — immutable, ONLY stores hash (per spec §3.3)
   - Private constructor (only via factory method)
   - `static UserPassword hash(plainPassword, hasher)` — factory for hashing
   - `boolean matches(plainPassword, hasher)` — verify plaintext against hash
   - Never exposes plaintext or algorithm details
   - Implements equals/hashCode

3. **`UserProfile.java`** — immutable value object (per spec §3.4)
   - Fields: firstName, lastName, phoneNumber, preferredLanguage, notificationsEnabled
   - Constructor validates firstName/lastName non-empty
   - Method: `fullName()` returns "firstName lastName"
   - Implements equals/hashCode

4. **`Address.java`** — immutable value object (per spec §3.5)
   - Fields: id (nullable), street, number, complement, neighborhood, city, state, postalCode, label, isDefault
   - Constructor validates: street, number, city, state non-empty; CEP format `^\d{5}-?\d{3}$`
   - Throws IllegalArgumentException if validation fails
   - Implements equals/hashCode

5. **`Role.java`** enum (per spec §3.7):
   ```java
   public enum Role {
       CUSTOMER("customer", "Can browse catalog, checkout, view orders"),
       ADMIN("admin", "Full system access"),
       VENDOR("vendor", "Can manage own products");
   }
   ```

6. **`UserStatus.java`** enum (per spec §3.7):
   ```java
   public enum UserStatus {
       ACTIVE,    // Can log in
       INACTIVE,  // Soft-deleted
       LOCKED,    // Too many failed attempts
       PENDING    // Email not verified (future)
   }
   ```

7. **Domain exceptions** in `user-domain/src/main/java/.../user/domain/exception/`:
   - `UserNotFoundException.java`
   - `EmailAlreadyRegisteredException.java`
   - `InvalidPasswordException.java`
   - `InsufficientPermissionException.java`
   - `SessionExpiredException.java`

8. **`User.java`** — aggregate root (per spec §3.1)
   - Fields: id, email (UserEmail), passwordHash (UserPassword), profile (UserProfile), addresses (Set), roles (Set), status, createdAt, updatedAt, lastLoginAt, failedLoginAttempts
   - Private constructor (only via static factory)
   - **Business methods (domain logic, no framework):**
     - `static User create(email, passwordHash, profile)` — factory
     - `boolean canLogin()` — returns true if status ACTIVE AND failedLoginAttempts < 5
     - `boolean authenticate(plainPassword, hasher)` — calls passwordHash.matches()
     - `void incrementFailedLoginAttempts()` — increments counter, sets status LOCKED if >= 5
     - `void resetFailedLoginAttempts()` — sets to 0
     - `void setLastLoginAt(Instant)` — updates lastLoginAt and updatedAt
     - `boolean hasRole(Role)` — checks if role in roles set
     - `void addRole(Role)` — adds role to set
     - `void addAddress(Address, boolean setAsDefault)` — adds address, optionally marks default
     - `void setDefaultAddress(Long addressId)` — unmarks all others, marks this one
     - `void removeAddress(Long addressId)` — throws if removing only address
     - `void changePassword(currentPassword, newPassword, hasher)` — validates current, sets new, resets failedLoginAttempts
   - All getters (no setters except via state-machine methods)
   - **Zero framework imports**

9. **Write `UserTest.java`** in `user-domain/src/test/java/`:
   - User creation, canLogin checks
   - authenticate() success/failure
   - incrementFailedLoginAttempts() + account lockout
   - Password change with current password verification
   - Address management (add, remove, set default)
   - Role checking
   - Status transitions
   - **Zero mocks used** (pure unit tests)

**Verification:**
```bash
mvn clean compile -pl user-domain
mvn test -pl user-domain -Dtest=UserTest
grep -r "jakarta\|javax" user-domain/src/main/java — should find NOTHING
```

**Done when:**
- [ ] No framework imports in domain package
- [ ] All value objects are immutable (final classes, private fields)
- [ ] UserTest passes 100%
- [ ] All business methods present and correct
- [ ] Domain exceptions defined and used

---

### STEP 2 — Create Application Ports & DTOs

**Read:** `user-account-implementation-sequence.md`, Step 2 + `user-account-module-spec.md`, Section 4

**Create use-case interfaces in `user-domain/src/main/java/.../user/application/port/in/`:**
- `RegisterUserUseCase.java`
- `LoginUseCase.java`
- `LogoutUseCase.java`
- `GetUserProfileUseCase.java`
- `UpdateProfileUseCase.java`
- `ChangePasswordUseCase.java`
- `RequestPasswordResetUseCase.java`
- `ResetPasswordUseCase.java`
- `AddAddressUseCase.java`
- `UpdateAddressUseCase.java`
- `DeleteAddressUseCase.java`
- `ListAddressesUseCase.java`
- `SetDefaultAddressUseCase.java`
- `GetCurrentUserUseCase.java`
- `CheckUserRoleUseCase.java`
- `AssignRoleUseCase.java`
- `ListUsersUseCase.java`

**Create outbound port interfaces in `user-domain/src/main/java/.../user/application/port/out/`:**
- `UserRepositoryPort.java` — save, findById, findByEmail, findAll (paginated), delete
- `PasswordHasherPort.java` — hash(plainPassword), verify(plainPassword, hash)
- `SessionPort.java` — createSession(user), getCurrentUser(), invalidateSession()
- `NotificationPort.java` — sendWelcomeEmail(user), sendPasswordResetEmail(user, token)
- `AuditLogPort.java` — logEvent(userId, eventType, ipAddress, userAgent, details)

**Create DTOs in `user-domain/src/main/java/.../user/application/`:**
- `UserSearchCriteria.java` (optional fields: status, email, fromDate, toDate, sortBy)
- `PageResult.java<T>` (items, totalElements, page, pageSize; method totalPages())

**Verify:**
- All interfaces compile with comprehensive Javadoc
- All DTOs are immutable (records or final classes)
- Zero framework imports in application package
- `mvn clean compile -pl user-domain` succeeds

**Done when:**
- [ ] All 17 use-case interfaces compile
- [ ] All 5 outbound ports compile
- [ ] All DTOs immutable
- [ ] Zero framework imports
- [ ] Comprehensive Javadoc on all methods

---

### STEP 3 — JPA Persistence Adapter

**Read:** `user-account-implementation-sequence.md`, Step 3 + `user-account-module-spec.md`, Section 3.8

Create in `catalog-adapters/src/main/java/.../user/adapter/persistence/`:

1. **Create `AuditableJpaEntity.java`** (reuse pattern from Order module if exists):
   ```java
   @MappedSuperclass
   public abstract class AuditableJpaEntity {
       @Version
       private Long version;
       
       @Column(name = "CREATED_AT", nullable = false, updatable = false)
       private Instant createdAt;
       
       @Column(name = "UPDATED_AT", nullable = false)
       private Instant updatedAt;
       
       @PrePersist
       void onCreate() {
           Instant now = Instant.now();
           createdAt = now;
           updatedAt = now;
       }
       
       @PreUpdate
       void onUpdate() {
           updatedAt = Instant.now();
       }
   }
   ```

2. **Create JPA entities in `entity/` subdirectory:**
   - `UserJpaEntity.java` (extends AuditableJpaEntity)
     - All columns from spec: email (unique), passwordHash, firstName, lastName, phone, preferredLanguage, notificationsEnabled, status, lastLoginAt, failedLoginAttempts, passwordResetToken, passwordResetTokenExpiresAt
     - @OneToMany addresses with cascade ALL, orphanRemoval true
     - @ManyToMany roles with FetchType.EAGER (so hasRole() is instant)
   
   - `UserAddressJpaEntity.java`
     - @ManyToOne user (FK cascade delete)
     - All address fields: street, number, complement, neighborhood, city, state, postalCode, label, isDefault
   
   - `UserRoleJpaEntity.java`
     - role enum (CUSTOMER, ADMIN, VENDOR)
     - description
   
   - `UserAuditLogJpaEntity.java`
     - userId (not FK, to support deleted users)
     - eventType (LOGIN_SUCCESS, LOGIN_FAILED, REGISTRATION, PASSWORD_CHANGED, LOGOUT, ROLE_ASSIGNED)
     - ipAddress, userAgent, details, createdAt

3. **Create `UserJpaMapper.java`:**
   - `mapToDomain(UserJpaEntity)` — unwraps value objects, returns User domain object
   - `mapToJpa(User)` — wraps value objects, returns UserJpaEntity
   - Both directions tested for correctness

4. **Create Flyway migration `V5__user_account_schema.sql`:**
   - USER_ACCOUNT table (all columns, email unique, indexes)
   - USER_ADDRESS table (FK user_id cascade delete, index)
   - ROLE table (enum roles: CUSTOMER, ADMIN, VENDOR)
   - USER_ROLE M:N join table (FK both cascade)
   - USER_AUDIT_LOG table (audit trail)
   - Indexes: user.email, user.status, address.user_id, audit_log.user_id, audit_log.created_at

5. **Write tests:**
   - `UserJpaMapperTest.java` — round-trip domain ↔ JPA for all fields
   - `UserRepositoryJpaAdapterTest.java` (Testcontainers, real DB) — CRUD, search by email, search by status, pagination

**Verification:**
```bash
mvn clean compile -pl catalog-adapters
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/onlineshop_test
mvn test -pl catalog-adapters -Dtest=UserJpaMapperTest,UserRepositoryJpaAdapterTest
```

**Done when:**
- [ ] Migration applies to test DB
- [ ] All tables/columns/indexes created correctly
- [ ] UserJpaMapperTest passes
- [ ] UserRepositoryJpaAdapterTest passes
- [ ] No class outside adapter.persistence imports UserJpaEntity

---

### STEP 4 — Password Hashing (Argon2id)

**Read:** `user-account-implementation-sequence.md`, Step 4

Create in `catalog-adapters/src/main/java/.../user/adapter/auth/`:

1. **Create `PasswordHasherArgon2Adapter.java`:**
   ```java
   @ApplicationScoped
   public class PasswordHasherArgon2Adapter implements PasswordHasherPort {
       private static final Argon2 argon2 = new Argon2Factory.create();
       
       @Override
       public String hash(String plainPassword) {
           return argon2.hash(Argon2Hash.UNOPTIMIZED, plainPassword);
       }
       
       @Override
       public boolean verify(String plainPassword, String hash) {
           try {
               return argon2.verify(hash, plainPassword);
           } catch (Exception e) {
               return false;
           }
       }
   }
   ```

2. **Create `PasswordHasherConfig.java`** (CDI Producer):
   ```java
   @ApplicationScoped
   public class PasswordHasherConfig {
       @Produces
       @ApplicationScoped
       PasswordHasherPort passwordHasher() {
           return new PasswordHasherArgon2Adapter();
       }
   }
   ```

3. **Write `PasswordHasherArgon2AdapterTest.java`:**
   - hash() returns Argon2id hash (not plaintext)
   - verify() with correct password returns true
   - verify() with wrong password returns false
   - Multiple hashes of same password produce different results
   - Weak password rejection

**Done when:**
- [ ] `mvn test -pl catalog-adapters -Dtest=PasswordHasherArgon2AdapterTest` passes
- [ ] No plaintext passwords stored anywhere

---

### STEP 5 — Session Management & @CurrentUser Injection

**Read:** `user-account-implementation-sequence.md`, Step 5

Create in `catalog-adapters/src/main/java/.../user/adapter/session/`:

1. **Create `@CurrentUser` qualifier:**
   ```java
   @Qualifier
   @Retention(RetentionPolicy.RUNTIME)
   @Target({ElementType.FIELD, ElementType.PARAMETER})
   public @interface CurrentUser {}
   ```

2. **Create `SessionAdapter.java`** (implements SessionPort):
   ```java
   @ApplicationScoped
   public class SessionAdapter implements SessionPort {
       @Inject
       private HttpServletRequest request;
       
       @Override
       public void createSession(User user) {
           HttpSession session = request.getSession(true);
           session.setAttribute("user", user);
           session.setMaxInactiveInterval(30 * 60);  // 30 min
       }
       
       @Override
       public Optional<User> getCurrentUser() {
           HttpSession session = request.getSession(false);
           if (session == null) return Optional.empty();
           Object user = session.getAttribute("user");
           return user instanceof User ? Optional.of((User) user) : Optional.empty();
       }
       
       @Override
       public void invalidateSession() {
           HttpSession session = request.getSession(false);
           if (session != null) session.invalidate();
       }
   }
   ```

3. **Create `CurrentUserProducer.java`:**
   ```java
   @ApplicationScoped
   public class CurrentUserProducer {
       @Inject
       private SessionPort session;
       
       @Produces
       @CurrentUser
       public User getCurrentUser() {
           return session.getCurrentUser()
               .orElseThrow(() -> new SessionExpiredException("User not logged in"));
       }
   }
   ```

4. **Write `SessionAdapterTest.java`:**
   - createSession stores user in HttpSession
   - getCurrentUser retrieves user from session
   - getCurrentUser returns empty when no session
   - invalidateSession destroys session
   - Session timeout after 30 min

**Done when:**
- [ ] `mvn test -pl catalog-adapters -Dtest=SessionAdapterTest` passes
- [ ] @CurrentUser injection works in JSF beans
- [ ] Session cookies have Secure, HttpOnly, SameSite flags

---

### STEPS 6–17: Remaining Implementation

For full details, see `user-account-implementation-sequence.md` sections 6–17:

- **STEP 6:** UserRepositoryJpaAdapter (CRUD, search by email/status, pagination)
- **STEP 7:** NotificationPort + adapters (welcome email, password reset email)
- **STEP 8:** AuditLogPort + AuditLogJpaAdapter (log all auth events)
- **STEP 9:** UserApplicationService (orchestrates all 17 use cases)
- **STEP 10:** LoginBean + login.xhtml (JSF login form, @RequestScoped)
- **STEP 11:** RegisterBean + register.xhtml (JSF registration form)
- **STEP 12:** ProfileBean + profile.xhtml (update name, phone, language)
- **STEP 13:** AddressBookBean + address-book.xhtml (CRUD addresses, set default)
- **STEP 14:** ChangePasswordBean + password-change.xhtml (change password)
- **STEP 15:** PasswordResetBean + password-reset.xhtml + password-reset-confirm.xhtml (forgot password)
- **STEP 16:** AdminUsersBean + admin/users.xhtml (user list, admin only, @RolesAllowed("ADMIN"))
- **STEP 17:** ArchUnit hexagonal-boundary tests (domain/application/adapter enforcement)

**For each step, follow the same pattern:**
1. Read the step in `user-account-implementation-sequence.md`
2. Read relevant sections in `user-account-module-spec.md`
3. Review relevant story in `user-account-backlog.md`
4. Write code in the specified packages
5. Write tests (unit + integration where specified)
6. Run verification commands
7. Verify "Done when" checklist passes
8. Commit before moving to next step

---

## Mandatory Code Quality Checklist

Every file you create must pass ALL of these checks before it's considered complete:

- [ ] **Zero framework imports in domain** — `grep -r "jakarta\|javax" user-domain/src/main/java/user/domain` returns nothing
- [ ] **No DAO classes** — all persistence via `UserRepositoryPort` interface
- [ ] **No @Stateless/@EJB** — use `@ApplicationScoped + @Transactional` instead
- [ ] **Hexagonal boundaries enforced** — domain doesn't know adapters, adapters don't know each other
- [ ] **No hardcoded configuration** — all secrets/keys via environment variables or @ConfigProperty
- [ ] **100% Jakarta EE** — zero `javax.*` imports anywhere
- [ ] **Comprehensive tests** — unit tests for domain, integration tests for adapters
- [ ] **Javadoc on all public classes/methods** — clear purpose, parameters, exceptions
- [ ] **Follows coding standards** — `java-jakarta-ee-coding-standards.md` applied to every file
- [ ] **All acceptance criteria met** — every story's acceptance criteria verified by tests
- [ ] **No plaintext passwords stored** — only Argon2id hashes in DB
- [ ] **Session security** — cookies have Secure, HttpOnly, SameSite flags

---

## Deployment & Verification

After all 17 steps are complete:

```bash
# Full clean build
mvn clean install

# Run all User Account tests
mvn test -Dtest=*User*,*Login*,*Register*,*Profile*,*Address*,*Password*,*Session*,*Audit*

# Run ArchUnit tests (last step)
mvn test -Dtest=UserHexagonalArchitectureTest

# Build deployable EAR
mvn clean install -pl ear

# Deploy to Open Liberty and smoke test:
# 1. Open browser → http://localhost:9080/onlineshop/register
# 2. Register new user: email, password, name
# 3. See welcome email (mocked)
# 4. Logout
# 5. Login with registered credentials
# 6. Update profile (name, phone)
# 7. Manage addresses (add, set default)
# 8. Logout
```

---

## Critical Success Factors

**DO's ✅:**
- ✅ Follow Product Catalog & Order & Checkout patterns **exactly**
- ✅ Make every adapter behind a port interface — swappable implementations
- ✅ Enforce RBAC via @RolesAllowed at application service + JSF template level
- ✅ Hash passwords with Argon2id (never plaintext)
- ✅ Use HttpSession for authentication (Phase 1)
- ✅ Implement audit logging for all auth events
- ✅ Write comprehensive tests — 80%+ code coverage on domain + services
- ✅ Use Open Liberty only — verified via Maven profiles

**DON'Ts ❌:**
- ❌ Do NOT add DAO classes — only Repository ports
- ❌ Do NOT use @EJB or @Stateless — use CDI @ApplicationScoped + @Transactional
- ❌ Do NOT hardcode credentials or API keys
- ❌ Do NOT store plaintext passwords — only hashes
- ❌ Do NOT skip session security (HttpOnly, Secure, SameSite)
- ❌ Do NOT mix framework imports in domain layer
- ❌ Do NOT skip audit logging

---

## What to Do If You Get Stuck

1. **Read the relevant section of `user-account-implementation-sequence.md`** — it has step-by-step actions, code snippets, and "Done when" checklists
2. **Check `user-account-module-spec.md`** — architecture, design decisions, rationale
3. **Review `user-account-backlog.md`** — acceptance criteria for the story you're on
4. **Look at Product Catalog & Order implementations** — they're the reference; same patterns apply here
5. **If still stuck:** Stop and report the issue with:
   - What step you're on
   - What code you've written so far
   - What compilation/test error you're seeing
   - Do NOT guess or improvise — ask for clarification

---

## Definition of Done for This Epic

The entire User Account module is complete when:

- [ ] All 17 steps completed
- [ ] All 17 backlog stories' acceptance criteria verified by tests
- [ ] No `javax.*` imports remain anywhere in user-domain or user-adapters
- [ ] ArchUnit hexagonal boundary tests passing
- [ ] E2E flow works end-to-end (register → login → profile update → logout)
- [ ] RBAC enforcement working (admin features protected by @RolesAllowed)
- [ ] Audit logging complete (all auth events logged)
- [ ] Passwords hashed with Argon2id (never plaintext stored)
- [ ] Session security enforced (HttpOnly, Secure, SameSite cookies)
- [ ] `mvn clean install` succeeds project-wide
- [ ] EAR deployable to Open Liberty
- [ ] Zero unhandled exceptions in logs on normal flow

---

**Status: ~~READY FOR IMPLEMENTATION~~ → SUPERSEDED (implemented and verified, July 30, 2026)** — see the banner at the top of this document; do not execute.

Good luck! 🚀
