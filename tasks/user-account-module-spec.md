# User Account Module — Implementation Specification

**Audience:** this document is written for an AI coding agent that will implement the changes directly in the `ecommerce` repository. It assumes the stack and conventions already established in the project's `java-jakarta-ee-coding-standards.md` (Jakarta EE 11, multi-module Maven layout, CDI/hexagonal architecture, JPA, JSF/Facelets, Open Liberty) **and the patterns established by the Product Catalog and Order & Checkout modules**, which serve as reference implementations.

**Status:** implemented and verified. Section 14 lists assumptions the implementer should flag back to the human if they turn out to be wrong. Companion documents: `user-account-backlog.md` (story breakdown) and `user-account-implementation-sequence.md` (session completion record).

> ## ✅ IMPLEMENTED — Read this before the body (July 30, 2026)
>
> The module described below has been **built and verified**. The body of this spec was written before implementation and assumes a repo layout (`user-domain`, `catalog-adapters`, `catalog-web`, Open Liberty) that **does not match the actual codebase**. Where the body conflicts with this box, **this box wins**.
>
> **Actual module layout (authoritative):**
> - One Maven module: **`user-account`** (package root `com.loja.useraccount`), UI pages in the **`web`** module. No `user-domain`/`catalog-adapters`/`catalog-web` modules exist.
> - Hexagonal layers: `domain/` (models, events, exceptions, validation), `domain/port/in/` (use cases), `domain/port/out/` (outbound ports), `application/` (service + DTOs), `adapter/in/web/` (thin JSF beans), `adapter/out/` (persistence, notification, event observers), `adapter/session/`, `adapter/auth/`.
> - **Ports live under `domain/port/`** (in + out), NOT under `application/port/` as the body shows. The framework-free rule applies to `domain` only; `application` may use CDI/`@Transactional` (enforced by `UserHexagonalArchitectureTest`, ArchUnit).
>
> **Deviations from the body (decided during implementation):**
> - **IDs are `String` UUIDs** (`VARCHAR(36)` in DB), not `Long` auto-increment. `UserRepositoryPort`/use cases use `String userId`.
> - Value object is named **`Email`** (not `UserEmail`); `UserProfile` keeps firstName/lastName/phoneNumber/preferredLanguage/notificationsEnabled and adds `fromFullName(String)`/`tryFromFullName` (registration uses a single `fullName` input). Passwords hashed via `UserPassword.hash(...)`.
> - **Roles:** CUSTOMER (default at creation), ADMIN, VENDOR — stored directly in the `user_role` table (`user_id` + `role` VARCHAR). There is **no `ROLE` lookup table** and no `UserRoleJpaEntity` join entity (the body shows a `ROLE` table + M:N join — not implemented).
> - `UserJpaEntity` is a single flattened entity (email, full_name, password_hash, status, active, timestamps, reset-token columns); addresses are stored in a `user_address` table keyed by `user_id` + `address_id` (no surrogate PK). See `flyway/sql/V5__user_account_schema.sql`.
> - **Service wiring:** `UserApplicationService` injects `UserRepositoryPort`, `PasswordHasherPort`, `SessionPort`, and `DomainEventPublisherPort`; it publishes **domain events** (`UserRegisteredEvent`, `UserLoggedInEvent`, `PasswordChangedEvent`, `PasswordResetRequestedEvent`, `AddressAddedEvent`, `AddressRemovedEvent`). Emails and audit logging run as **CDI observers** (`adapter/out/event/WelcomeEmailObserver`, `PasswordResetEmailObserver`, `AuditLogObserver`) — the service does not call `NotificationPort`/`AuditLogPort` directly as the body shows.
> - `NotificationPort` signature: `sendWelcomeEmail(String email, String fullName)`, `sendPasswordResetEmail(String email, String token)` (no `User` param, no `NotificationException`).
> - **Session context:** both `@CurrentUser` (`adapter/session/CurrentUserProducer`) **and** the `@SessionScoped` `UserBean` (template guards `#{userBean.hasRole('ADMIN')}`, `#{userBean.currentUser}`, `#{userBean.loggedIn}`) exist.
> - Password hashing is `PasswordHasherArgon2Adapter` in `adapter/auth/`.
> - `PasswordResetBean` + `password-reset.xhtml` / `password-reset-confirm.xhtml` and `AdminUsersBean` + `admin/users.xhtml` (PAGE_SIZE 20, email/status filter, `assignRole`) are implemented; `findAll` sorts by `email` for deterministic pagination; `findByResetToken(token)` added to `UserRepositoryPort`.
>
> **Verified state:** `mvn test -pl user-account` → 117 tests green (108 unit + 9 IT); root `mvn test` → all modules SUCCESS; `mvn compile -pl web -am` → SUCCESS. V5 migration applied and registered in `flyway_schema_history` (see implementation-sequence doc for details).

---

## 0. Architecture: Hexagonal with RBAC & Session-Based Authentication

This module implements **user authentication and authorization** following the established hexagonal pattern. The key difference from previous modules: User Account is **cross-cutting** — it provides authentication context to all other modules (Catalog, Order, Checkout, etc.).

**Authentication Strategy (Phase 1):**

- **Session-based:** HttpSession managed by Open Liberty (not JWT yet)
- **RBAC (Role-Based Access Control):** Users have roles (CUSTOMER, ADMIN); endpoints enforce @RolesAllowed
- **Password Security:** Argon2id hashing (never store plaintext)
- **CSRF Protection:** JSF ViewState tokens (automatic)

**Future Phase 2 (not in scope):**

- JWT for mobile/REST API (coexist with Session)
- OAuth2/OpenID Connect for third-party integration

**Dependency Diagram:**

```
Domain (user.domain)
    ↑
Application (user.application.port.in + .service)
    ↑
Adapters:
  - Persistence (user.adapter.persistence) — UserJpaEntity, UserRoleJpaEntity
  - Authentication (user.adapter.auth) — PasswordHasher (Argon2id wrapper), SessionManager
    ↑
Web (user.web.jsf.beans)
    ↓
  Other modules (Catalog, Order) depend on UserAccount ports (GetUserUseCase, CheckRoleUseCase)
```

**Critical:** User Account module is **one of few exceptions** where other modules may depend on it (authentication is cross-cutting). But User Account domain **does not depend on Catalog/Order** — unidirectional dependency.

---

## 1. Purpose & Scope

Enable user registration, authentication, and role-based access control. Provide identity context to all other modules so features can be personalized per user (cart ownership, order history by customer, wishlist, etc.).

**In scope:**

- User registration (email, password, basic profile)
- Login / logout (session-based)
- Profile management (name, phone, preferred language, notification preferences)
- Address book (multiple addresses per user, set default)
- RBAC: roles (CUSTOMER, ADMIN), role assignment
- Password reset (email-based token, not SMS)
- User deletion (soft-delete: mark inactive, not hard-delete)
- Session management (timeout, concurrent login control)
- Audit logging (login attempts, password changes, role changes)

**Out of scope (explicitly deferred):**

- OAuth2 / OpenID Connect / SAML — future Phase 2
- JWT tokens — future Phase 2
- Two-factor authentication (2FA/TOTP) — future feature
- Social login (Google, Facebook) — future feature
- LDAP/Active Directory sync — future feature
- Granular permissions (beyond roles) — future feature

---

## 2. Module / Package Layout

```
user-domain/src/main/java/.../user/domain/
├── User.java                        (aggregate root, no JPA)
├── UserProfile.java                 (value object: name, phone, preferences)
├── UserEmail.java                   (value object: email with validation)
├── UserPassword.java                (value object: password hash, never plaintext)
├── Address.java                     (value object: street, city, CEP, etc.)
├── Role.java                        (enum: CUSTOMER, ADMIN)
├── exception/
│   ├── UserNotFoundException.java
│   ├── EmailAlreadyRegisteredException.java
│   ├── InvalidPasswordException.java
│   ├── InsufficientPermissionException.java
│   └── SessionExpiredException.java

user-domain/src/main/java/.../user/application/
├── port/in/
│   ├── RegisterUserUseCase.java
│   ├── LoginUseCase.java
│   ├── LogoutUseCase.java
│   ├── GetUserProfileUseCase.java
│   ├── UpdateProfileUseCase.java
│   ├── ChangePasswordUseCase.java
│   ├── RequestPasswordResetUseCase.java
│   ├── ResetPasswordUseCase.java
│   ├── AddAddressUseCase.java
│   ├── UpdateAddressUseCase.java
│   ├── DeleteAddressUseCase.java
│   ├── ListAddressesUseCase.java
│   ├── SetDefaultAddressUseCase.java
│   ├── GetCurrentUserUseCase.java
│   ├── CheckUserRoleUseCase.java
│   ├── AssignRoleUseCase.java
│   └── ListUsersUseCase.java        (admin only)
├── port/out/
│   ├── UserRepositoryPort.java
│   ├── PasswordHasherPort.java
│   ├── SessionPort.java
│   ├── NotificationPort.java        (sends password reset emails)
│   └── AuditLogPort.java            (logs auth events)
├── service/
│   ├── UserApplicationService.java  (orchestrates all use cases)
│   ├── PasswordResetTokenService.java (generates/validates reset tokens)
│   └── UserSearchCriteria.java, PageResult.java (DTOs)

catalog-adapters/src/main/java/.../user/adapter/persistence/
├── entity/
│   ├── UserJpaEntity.java
│   ├── UserAddressJpaEntity.java
│   ├── UserRoleJpaEntity.java
│   ├── UserAuditLogJpaEntity.java
│   └── AuditableJpaEntity.java      (reuse from Order module)
├── UserJpaMapper.java
├── UserRepositoryJpaAdapter.java
├── PasswordHasherArgon2Adapter.java
├── AuditLogJpaAdapter.java

catalog-web/servlet/src/main/java/.../user/web/jsf/beans/
├── LoginBean.java                   (@RequestScoped)
├── RegisterBean.java                (@RequestScoped)
├── ProfileBean.java                 (@ViewScoped)
├── AddressBookBean.java             (@ViewScoped)
├── AdminUsersBean.java              (@ViewScoped, @RolesAllowed("ADMIN"))
├── UserBean.java                    (@SessionScoped) — current logged-in user context
├── CurrentUserHolder.java           (CDI producer for @CurrentUser injection)

catalog-web/servlet/src/main/webapp/
├── login.xhtml
├── register.xhtml
├── profile.xhtml
├── address-book.xhtml
├── password-reset.xhtml
├── password-reset-confirm.xhtml
└── admin/
    └── users.xhtml
```

---

## 3. Domain Model

### 3.1 `User` Domain Object (Core Aggregate)

```java
public final class User {
    private UserId id;
    private UserEmail email;            // unique, immutable
    private UserPassword passwordHash;  // never plaintext
    private UserProfile profile;        // name, phone, preferences
    private Set<Address> addresses;     // multiple addresses, one marked default
    private Set<Role> roles;            // CUSTOMER, ADMIN, etc.
    private UserStatus status;          // ACTIVE, INACTIVE (soft-delete)
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private Integer failedLoginAttempts;
}
```

**Business Methods (Domain Logic):**

1. **`authenticate(plainPassword: String): boolean`** — compares plaintext with stored hash
   - Returns true if password matches
   - Resets `failedLoginAttempts` to 0 on success
   - Increments on failure (used to detect brute force)
   - Throws `InvalidPasswordException` if > 5 failures (lock account)

2. **`changePassword(currentPassword, newPassword)`** — enforces current password validation
   - Verify current password is correct (call `authenticate()`)
   - Set new password hash
   - Reset `failedLoginAttempts` to 0

3. **`hasRole(role: Role): boolean`** — checks if user has a role
   - Used by application service for authorization checks

4. **`addAddress(address: Address, setAsDefault: boolean)`** — adds address to set
   - If `setAsDefault`, unmark all others as default, mark this one

5. **`setDefaultAddress(addressId: Long)`** — sets one address as default
   - Unmark previous default
   - Mark new default

6. **`removeAddress(addressId: Long)`** — removes address
   - Cannot remove default address unless user has others

7. **`requestPasswordReset(token: String)`** — records reset request
   - Stores token + expiry timestamp
   - Token expires in 24 hours

8. **`resetPassword(token: String, newPassword: String)`** — completes password reset
   - Validate token not expired
   - Validate token matches stored token
   - Set new password
   - Clear token

9. **`markInactive()`** — soft-delete (status = INACTIVE)
   - Does not remove data, only hides from queries

10. **`canLogin(): boolean`** — checks if user can log in
    - Status must be ACTIVE
    - Failed attempts < 5 (not locked)
    - Returns false otherwise

### 3.2 `UserEmail` Value Object

Immutable email with validation:

```java
public final class UserEmail {
    private final String value;

    public UserEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank())
            throw new IllegalArgumentException("Email cannot be empty");
        String normalized = rawEmail.trim().toLowerCase();
        if (!isValidEmailFormat(normalized))
            throw new IllegalArgumentException("Invalid email format: " + normalized);
        this.value = normalized;
    }

    private static boolean isValidEmailFormat(String email) {
        // RFC 5322 simplified regex or use Jakarta Bean Validation @Email
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    public String value() { return value; }
}
```

### 3.3 `UserPassword` Value Object

Never stores plaintext; only stores hash. Construction requires hashing:

```java
public final class UserPassword {
    private final String hash;  // Argon2id hash

    private UserPassword(String hash) {
        this.hash = hash;  // private constructor
    }

    /**
     * Factory method: hash plaintext password.
     * Called by domain when setting/changing password.
     * Hashing happens in domain, not in adapter.
     * (Adapter receives UserPassword already hashed)
     */
    public static UserPassword hash(String plainPassword, PasswordHasher hasher) {
        if (plainPassword == null || plainPassword.length() < 8)
            throw new IllegalArgumentException("Password must be >= 8 characters");
        String hash = hasher.hash(plainPassword);
        return new UserPassword(hash);
    }

    /**
     * Verify plaintext against stored hash.
     */
    public boolean matches(String plainPassword, PasswordHasher hasher) {
        return hasher.verify(plainPassword, this.hash);
    }

    public String getHash() { return hash; }
}
```

### 3.4 `UserProfile` Value Object

```java
public final class UserProfile {
    private final String firstName;
    private final String lastName;
    private final String phoneNumber;   // optional
    private final String preferredLanguage;  // "pt_BR", "en_US"
    private final boolean notificationsEnabled;

    public UserProfile(String firstName, String lastName, String phoneNumber, String lang, boolean notif) {
        if (firstName == null || firstName.isBlank())
            throw new IllegalArgumentException("First name required");
        if (lastName == null || lastName.isBlank())
            throw new IllegalArgumentException("Last name required");
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.preferredLanguage = lang;
        this.notificationsEnabled = notif;
    }

    public String fullName() { return firstName + " " + lastName; }
}
```

### 3.5 `Address` Value Object

```java
public final class Address {
    private final Long id;               // nullable until persisted
    private final String street;
    private final String number;
    private final String complement;
    private final String neighborhood;
    private final String city;
    private final String state;
    private final String postalCode;     // CEP
    private final String label;          // "Home", "Work", etc.
    private final boolean isDefault;

    // Constructor validates all fields, CEP format, etc.
}
```

### 3.6 `Role` Enum

```java
public enum Role {
    CUSTOMER("customer", "Can browse catalog, checkout, view orders"),
    ADMIN("admin", "Full system access"),
    VENDOR("vendor", "Can manage own products (future)");

    private final String code;
    private final String description;
}
```

### 3.7 `UserStatus` Enum

```java
public enum UserStatus {
    ACTIVE,      // Can log in
    INACTIVE,    // Soft-deleted, cannot log in
    LOCKED,      // Too many failed login attempts
    PENDING      // Email not yet verified (future feature)
}
```

### 3.8 JPA Entities (Adapter Layer Only)

These live in `catalog-adapters/.../user/adapter/persistence/entity/`, **not** in domain.

`UserJpaEntity` (extends `AuditableJpaEntity`):

```java
@Entity
@Table(name = "USER_ACCOUNT", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class UserJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 500)
    private String passwordHash;  // Argon2id hash

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 10)
    private String preferredLanguage;  // "pt_BR", "en_US"

    @Column(nullable = false)
    private Boolean notificationsEnabled;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column
    private Instant lastLoginAt;

    @Column
    private Integer failedLoginAttempts;

    @Column(length = 500)
    private String passwordResetToken;

    @Column
    private Instant passwordResetTokenExpiresAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserAddressJpaEntity> addresses = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "USER_ROLE",
        joinColumns = @JoinColumn(name = "USER_ID"),
        inverseJoinColumns = @JoinColumn(name = "ROLE_ID")
    )
    private Set<UserRoleJpaEntity> roles = new HashSet<>();

    // getters/setters, no business logic
}
```

`UserAddressJpaEntity`:

```java
@Entity
@Table(name = "USER_ADDRESS")
public class UserAddressJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private UserJpaEntity user;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(length = 100)
    private String complement;

    @Column(nullable = false, length = 100)
    private String neighborhood;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    @Column(nullable = false, length = 10)
    private String postalCode;  // CEP

    @Column(length = 50)
    private String label;  // "Home", "Work"

    @Column(nullable = false)
    private Boolean isDefault;
}
```

`UserRoleJpaEntity`:

```java
@Entity
@Table(name = "ROLE")
public class UserRoleJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    @Enumerated(EnumType.STRING)
    private Role role;  // CUSTOMER, ADMIN, VENDOR

    @Column(length = 255)
    private String description;
}
```

`UserAuditLogJpaEntity`:

```java
@Entity
@Table(name = "USER_AUDIT_LOG")
public class UserAuditLogJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String eventType;  // LOGIN, LOGOUT, PASSWORD_CHANGE, ROLE_CHANGE, etc.

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    @Column(length = 500)
    private String details;

    @Column(nullable = false)
    private Instant createdAt;
}
```

---

## 4. Application Ports (Interfaces)

### 4.1 Inbound Ports (Use Cases)

All in `user-domain/src/main/java/.../user/application/port/in/`:

```java
public interface RegisterUserUseCase {
    /**
     * Registers a new user with email and password.
     * Throws EmailAlreadyRegisteredException if email already exists.
     */
    User registerUser(String email, String password, String firstName, String lastName)
        throws EmailAlreadyRegisteredException;
}

public interface LoginUseCase {
    /**
     * Authenticates user by email and password.
     * Returns User if successful, throws InvalidPasswordException if password wrong.
     * Logs login attempt (success/failure) in audit log.
     * Increments failedLoginAttempts on failure; locks account if > 5.
     */
    User login(String email, String password) throws InvalidPasswordException;
}

public interface LogoutUseCase {
    void logout(Long userId);  // Invalidates session
}

public interface GetUserProfileUseCase {
    Optional<User> getUserProfile(Long userId);
}

public interface UpdateProfileUseCase {
    User updateProfile(Long userId, String firstName, String lastName, String phone, String language, boolean notificationsEnabled);
}

public interface ChangePasswordUseCase {
    void changePassword(Long userId, String currentPassword, String newPassword) throws InvalidPasswordException;
}

public interface RequestPasswordResetUseCase {
    /**
     * Generates a password reset token, stores it, and sends email to user.
     * Token expires in 24 hours.
     */
    void requestPasswordReset(String email) throws UserNotFoundException;
}

public interface ResetPasswordUseCase {
    /**
     * Validates reset token and sets new password.
     * Throws exception if token invalid/expired.
     */
    void resetPassword(String token, String newPassword) throws InvalidPasswordException;
}

public interface AddAddressUseCase {
    Address addAddress(Long userId, String street, String number, String complement,
                      String neighborhood, String city, String state, String postalCode,
                      String label, boolean setAsDefault);
}

public interface UpdateAddressUseCase {
    Address updateAddress(Long userId, Long addressId, String street, String number,
                         String complement, String neighborhood, String city, String state,
                         String postalCode, String label);
}

public interface DeleteAddressUseCase {
    void deleteAddress(Long userId, Long addressId) throws IllegalArgumentException;
}

public interface ListAddressesUseCase {
    List<Address> listAddresses(Long userId);
}

public interface SetDefaultAddressUseCase {
    void setDefaultAddress(Long userId, Long addressId);
}

public interface GetCurrentUserUseCase {
    /**
     * Gets the currently logged-in user from the session.
     * Returns empty if not logged in.
     */
    Optional<User> getCurrentUser();
}

public interface CheckUserRoleUseCase {
    /**
     * Checks if current user has a specific role.
     * Used by authorization logic (e.g., can user access admin features).
     */
    boolean currentUserHasRole(Role role);
}

public interface AssignRoleUseCase {
    /**
     * Admin-only: assigns a role to a user.
     */
    void assignRole(Long userId, Role role) throws InsufficientPermissionException;
}

public interface ListUsersUseCase {
    /**
     * Admin-only: paginated list of all users.
     */
    PageResult<User> listUsers(int page, int pageSize, UserSearchCriteria criteria);
}
```

### 4.2 Outbound Ports (Adapter Boundaries)

All in `user-domain/src/main/java/.../user/application/port/out/`:

```java
public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(Long userId);
    Optional<User> findByEmail(String email);
    PageResult<User> findAll(int page, int pageSize, UserSearchCriteria criteria);
    void delete(Long userId);  // soft-delete (set status = INACTIVE)
}

public interface PasswordHasherPort {
    /**
     * Hashes a plaintext password using Argon2id.
     */
    String hash(String plainPassword);

    /**
     * Verifies plaintext against stored hash.
     */
    boolean verify(String plainPassword, String hash);
}

public interface SessionPort {
    /**
     * Creates a session for a logged-in user.
     * Stores User object in HttpSession.
     */
    void createSession(User user);

    /**
     * Gets current user from session.
     * Returns empty if not logged in.
     */
    Optional<User> getCurrentUser();

    /**
     * Invalidates session (logout).
     */
    void invalidateSession();
}

public interface NotificationPort {
    /**
     * Sends password reset email to user.
     */
    void sendPasswordResetEmail(User user, String resetToken) throws NotificationException;

    /**
     * Sends welcome email after registration.
     */
    void sendWelcomeEmail(User user) throws NotificationException;
}

public interface AuditLogPort {
    /**
     * Logs authentication events (login success/fail, password change, role change).
     */
    void logEvent(Long userId, String eventType, String ipAddress, String userAgent, String details);
}
```

---

## 5. Application Service

**`UserApplicationService`** implements all inbound use cases and orchestrates outbound port calls:

```java
@ApplicationScoped
@Transactional
public class UserApplicationService implements RegisterUserUseCase, LoginUseCase, LogoutUseCase,
                                               GetUserProfileUseCase, UpdateProfileUseCase,
                                               ChangePasswordUseCase, RequestPasswordResetUseCase,
                                               ResetPasswordUseCase, AddAddressUseCase,
                                               UpdateAddressUseCase, DeleteAddressUseCase,
                                               ListAddressesUseCase, SetDefaultAddressUseCase,
                                               GetCurrentUserUseCase, CheckUserRoleUseCase,
                                               AssignRoleUseCase, ListUsersUseCase {

    @Inject
    private UserRepositoryPort userRepository;

    @Inject
    private PasswordHasherPort passwordHasher;

    @Inject
    private SessionPort session;

    @Inject
    private NotificationPort notification;

    @Inject
    private AuditLogPort auditLog;

    @Override
    public User registerUser(String email, String password, String firstName, String lastName) {
        // 1. Check if email already exists
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException("Email already registered");
        }

        // 2. Create User domain object (password hashing in domain via factory method)
        UserEmail userEmail = new UserEmail(email);
        UserPassword hashedPassword = UserPassword.hash(password, passwordHasher);
        UserProfile profile = new UserProfile(firstName, lastName, null, "pt_BR", true);
        User user = User.create(userEmail, hashedPassword, profile);
        user.addRole(Role.CUSTOMER);  // default role

        // 3. Persist
        user = userRepository.save(user);

        // 4. Send welcome email (async, non-blocking)
        try {
            notification.sendWelcomeEmail(user);
        } catch (Exception e) {
            // Log but don't fail registration if email fails
            logger.warning("Welcome email failed for " + user.email());
        }

        // 5. Log event
        auditLog.logEvent(user.id(), "REGISTRATION", null, null, "New user registered");

        return user;
    }

    @Override
    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new InvalidPasswordException("Invalid email or password"));

        // 1. Check if user can log in
        if (!user.canLogin()) {
            auditLog.logEvent(user.id(), "LOGIN_FAILED", null, null, "Account locked or inactive");
            throw new InvalidPasswordException("Account is locked or inactive");
        }

        // 2. Authenticate (domain method compares password)
        if (!user.authenticate(password, passwordHasher)) {
            user.incrementFailedLoginAttempts();
            userRepository.save(user);
            auditLog.logEvent(user.id(), "LOGIN_FAILED", null, null, "Invalid password");
            throw new InvalidPasswordException("Invalid email or password");
        }

        // 3. Success: reset failed attempts, update last login
        user.resetFailedLoginAttempts();
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        // 4. Create session
        session.createSession(user);

        // 5. Log event
        auditLog.logEvent(user.id(), "LOGIN_SUCCESS", null, null, "Logged in successfully");

        return user;
    }

    @Override
    public void logout(Long userId) {
        session.invalidateSession();
        auditLog.logEvent(userId, "LOGOUT", null, null, "Logged out");
    }

    // ... other use cases
}
```

---

## 6. Database Schema & Migrations

**Flyway migration file:** `V5__user_account_schema.sql`

Tables:

- `USER_ACCOUNT` — users with email, password hash, profile, status
- `USER_ADDRESS` — addresses (multiple per user, one marked default)
- `ROLE` — roles (CUSTOMER, ADMIN, VENDOR)
- `USER_ROLE` — M:N join table
- `USER_AUDIT_LOG` — audit trail (login attempts, password changes, etc.)

Indexes:

- `USER_ACCOUNT.email` (unique, frequent login queries)
- `USER_ACCOUNT.status` (filter active users)
- `USER_ADDRESS.user_id` (find addresses by user)
- `USER_AUDIT_LOG.user_id, created_at DESC` (audit trail queries)

---

## 7. Web Layer (Login/Register UI, Profile, Address Book)

### 7.1 `LoginBean.java` (@RequestScoped)

Handles login form submission:

```java
@Named("loginBean")
@RequestScoped
public class LoginBean {
    @Inject
    private LoginUseCase loginUseCase;

    private String email;
    private String password;

    public String login() {
        try {
            User user = loginUseCase.login(email, password);
            // Session created by use case
            return "redirect:dashboard";
        } catch (InvalidPasswordException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Login Failed",
                    "Invalid email or password"));
            return null;  // stay on login page
        }
    }
}
```

### 7.2 `ProfileBean.java` (@ViewScoped)

Manages user profile and addresses:

```java
@Named("profileBean")
@ViewScoped
public class ProfileBean implements Serializable {
    @Inject
    @CurrentUser
    private User currentUser;  // injected from session

    @Inject
    private UpdateProfileUseCase updateProfileUseCase;

    private String firstName;
    private String lastName;
    private List<Address> addresses;

    @PostConstruct
    void loadProfile() {
        firstName = currentUser.profile().firstName();
        lastName = currentUser.profile().lastName();
        // ... load addresses
    }

    public void updateProfile() {
        try {
            updateProfileUseCase.updateProfile(currentUser.id(), firstName, lastName, ...);
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Profile updated"));
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));
        }
    }
}
```

### 7.3 JSF Pages

- **login.xhtml** — Email + password form, submit to LoginBean.login()
- **register.xhtml** — Email + password + name, submit to RegisterBean.register()
- **profile.xhtml** — Edit name, phone, language preference, notifications toggle
- **address-book.xhtml** — List addresses, add/edit/delete, set default
- **password-reset.xhtml** — Request password reset (email form)
- **password-reset-confirm.xhtml** — New password form (token in URL)

---

## 8. Session Management

### 8.1 SessionPort Implementation

```java
@ApplicationScoped
public class SessionAdapter implements SessionPort {

    @Override
    public void createSession(User user) {
        HttpServletRequest request = getHttpRequest();  // via CDI producer
        HttpSession session = request.getSession(true);
        session.setAttribute("user", user);
        session.setMaxInactiveInterval(30 * 60);  // 30 min timeout
    }

    @Override
    public Optional<User> getCurrentUser() {
        HttpServletRequest request = getHttpRequest();
        HttpSession session = request.getSession(false);
        if (session == null) return Optional.empty();
        Object user = session.getAttribute("user");
        return user instanceof User ? Optional.of((User) user) : Optional.empty();
    }

    @Override
    public void invalidateSession() {
        HttpServletRequest request = getHttpRequest();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
```

### 8.2 @CurrentUser CDI Qualifier

```java
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface CurrentUser {}
```

Producer:

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

**Usage:**

```java
@Inject
@CurrentUser
private User currentUser;  // automatically resolved from session
```

---

## 9. RBAC Implementation

### 9.1 @RolesAllowed Annotations

Protected endpoints (JSF beans, application services):

```java
@RolesAllowed("ADMIN")
public class AdminUsersBean {
    // Only ADMIN role can access
}

@ApplicationScoped
public class UserApplicationService implements AssignRoleUseCase {
    @RolesAllowed("ADMIN")  // or check in service
    public void assignRole(Long userId, Role role) {
        // ...
    }
}
```

### 9.2 Permission Checking in JSF

```xhtml
<h:panelGroup rendered="#{userBean.hasRole('ADMIN')}">
  <!-- Admin-only content -->
  <a href="admin/users">Manage Users</a>
</h:panelGroup>
```

**Note:** `UserBean` is @SessionScoped, exposes current user for template access.

---

## 10. Testing Requirements

**Domain Layer Tests** (`UserTest.java`):

- User creation, validation
- Password verification (Argon2 hashing)
- Address management
- Role checking
- Status transitions (ACTIVE → INACTIVE → ACTIVE)

**Application Service Tests** (`UserApplicationServiceTest.java`):

- Registration success + email already registered
- Login success + invalid password + account locked
- Password reset flow
- Profile updates
- Address CRUD

**Adapter Tests**:

- `UserRepositoryJpaAdapterTest` — Testcontainers with real DB
- `PasswordHasherArgon2AdapterTest` — Hash + verify correctness
- `SessionAdapterTest` — Session create/read/invalidate

**E2E Test**:

- Register new user → login → update profile → add address → logout

---

## 11. Security Considerations

- ✅ **Passwords hashed with Argon2id** (industry-standard, resistant to GPU attacks)
- ✅ **Session cookies: HttpOnly, Secure, SameSite=Strict**
- ✅ **CSRF protection: JSF ViewState tokens** (automatic)
- ✅ **Account lockout: 5 failed logins → status LOCKED**
- ✅ **Password reset tokens: 24-hour expiry**
- ✅ **Audit logging: all auth events recorded**
- ✅ **Soft-delete: user data retained for compliance**

---

## 12. Configuration & Externalization

Environment variables:

- `PASSWORD_RESET_TOKEN_EXPIRY_HOURS` (default: 24)
- `LOGIN_MAX_FAILED_ATTEMPTS` (default: 5)
- `SESSION_TIMEOUT_MINUTES` (default: 30)
- `NOTIFICATION_PROVIDER` (sendgrid|smtp|mock)

---

## 13. Future Phase 2 (Deferred, Not in Scope)

- JWT tokens for mobile/REST API
- OAuth2 / OpenID Connect
- Two-factor authentication (TOTP/SMS)
- Social login (Google, Facebook)
- Email verification on registration
- Granular permissions (beyond roles)

---

## 14. Open Questions / Assumptions for Review

1. ✅ **Session vs JWT:** Using HttpSession (Phase 1), JWT deferred to Phase 2
2. ✅ **RBAC scope:** 2 roles (CUSTOMER, ADMIN) for MVP. VENDOR role defined but not used yet
3. ✅ **Password requirements:** Min 8 characters. No complexity rules (can add later)
4. ✅ **Account lockout:** 5 failed attempts → status LOCKED. Auto-unlock after 1 hour? (Defer, manual admin unlock for now)
5. ✅ **Email verification:** On registration, require email verification? (Defer to Phase 2)
6. ✅ **Concurrent login:** Allow user to log in from multiple devices simultaneously? (Yes, for MVP)
7. ✅ **Soft-delete:** Users marked INACTIVE instead of deleted? (Yes, for GDPR compliance)
8. ✅ **Password reset:** Email-based token or SMS? (Email only, SMS deferred)
9. ✅ **Profile picture:** User avatar/profile photo support? (Defer to Phase 2)
10. ✅ **Wishlist:** Wishlist feature in scope or separate module? (Separate module, deferred)
