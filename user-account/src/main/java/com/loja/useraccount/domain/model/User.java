package com.loja.useraccount.domain.model;

import com.loja.shared.domain.Result;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import com.loja.useraccount.domain.validation.DomainError;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class User {

    private String id;
    private Email email;
    private UserPassword passwordHash;
    private UserProfile profile;
    private Set<Address> addresses;
    private Set<Role> roles;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private int failedLoginAttempts;
    private UserStatus status;
    private String passwordResetToken;
    private Instant passwordResetTokenExpiresAt;

    private User() {
    }

    public User(String id, Email email, UserPassword passwordHash, UserProfile profile) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.profile = profile;
        this.addresses = new HashSet<>();
        this.roles = new HashSet<>();
        this.roles.add(Role.CUSTOMER);
        this.active = true;
        this.status = UserStatus.ACTIVE;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastLoginAt = null;
        this.failedLoginAttempts = 0;
    }

    public static User create(Email email, UserPassword passwordHash, UserProfile profile) {
        return new User(java.util.UUID.randomUUID().toString(), email, passwordHash, profile);
    }

    public static Result<User, DomainError> tryRegister(String email, String password,
                                                         String fullName,
                                                         PasswordHasherPort hasher) {
        var emailResult = Email.tryCreate(email);
        if (emailResult.isFailure()) {
            return Result.failure(emailResult.getError().get());
        }
        var passwordResult = UserPassword.tryHash(password, hasher);
        if (passwordResult.isFailure()) {
            return Result.failure(passwordResult.getError().get());
        }
        var profileResult = UserProfile.tryFromFullName(fullName);
        if (profileResult.isFailure()) {
            return Result.failure(profileResult.getError().get());
        }
        return Result.success(new User(java.util.UUID.randomUUID().toString(),
                emailResult.getValue().get(),
                passwordResult.getValue().get(),
                profileResult.getValue().get()));
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean authenticate(String plainPassword, PasswordHasherPort hasher) {
        if (!passwordHash.matches(plainPassword, hasher)) {
            recordLoginFailure();
            return false;
        }
        recordSuccessfulLogin(Instant.now());
        return true;
    }

    public void changePassword(String currentPassword, String newPassword, PasswordHasherPort hasher) {
        if (!passwordHash.matches(currentPassword, hasher)) {
            throw new IllegalArgumentException("Current password is invalid");
        }
        this.passwordHash = UserPassword.hash(newPassword, hasher);
        this.failedLoginAttempts = 0;
        this.updatedAt = Instant.now();
    }

    public void addRole(Role role) {
        roles.add(role);
        updatedAt = Instant.now();
    }

    public void addAddress(Address address, boolean setAsDefault) {
        if (address == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }
        if (setAsDefault) {
            addresses.removeIf(existing -> existing.isDefault());
        }
        addresses.add(withId(address));
        updatedAt = Instant.now();
    }

    private Address withId(Address address) {
        if (address.getId() != null) {
            return address;
        }
        long nextId = addresses.stream()
                .map(Address::getId)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L) + 1L;
        return new Address(nextId, address.getStreet(), address.getNumber(), address.getComplement(),
                address.getNeighborhood(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getLabel(), address.isDefault());
    }

    public void setDefaultAddress(Long addressId) {
        Address target = addresses.stream()
                .filter(a -> Objects.equals(a.getId(), addressId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));
        Set<Address> updated = new HashSet<>();
        for (Address a : addresses) {
            boolean isTarget = Objects.equals(a.getId(), addressId);
            updated.add(new Address(a.getId(), a.getStreet(), a.getNumber(), a.getComplement(),
                    a.getNeighborhood(), a.getCity(), a.getState(), a.getPostalCode(),
                    a.getLabel(), isTarget));
        }
        this.addresses = updated;
        updatedAt = Instant.now();
    }

    public void removeAddress(Long addressId) {
        boolean removed = addresses.removeIf(address -> Objects.equals(address.getId(), addressId));
        if (!removed) {
            throw new IllegalStateException("Address not found");
        }
        if (addresses.isEmpty()) {
            throw new IllegalStateException("Cannot remove last address");
        }
        updatedAt = Instant.now();
    }

    public void updateProfile(UserProfile newProfile) {
        this.profile = newProfile;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.status = UserStatus.INACTIVE;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.status = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.updatedAt = Instant.now();
    }

    /** Restores a previously persisted password-reset request when reconstructing the aggregate. */
    public void restorePasswordReset(String token, Instant expiresAt) {
        this.passwordResetToken = token;
        this.passwordResetTokenExpiresAt = expiresAt;
    }

    public void recordLoginFailure() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.status = UserStatus.LOCKED;
        }
        this.updatedAt = Instant.now();
    }

    public void requestPasswordReset(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token cannot be empty");
        }
        this.passwordResetToken = token;
        this.passwordResetTokenExpiresAt = Instant.now().plusSeconds(24 * 60 * 60); // 24-hour expiry
        this.updatedAt = Instant.now();
    }

    public boolean isResetTokenValid(String token) {
        return passwordResetToken != null
                && passwordResetToken.equals(token)
                && passwordResetTokenExpiresAt != null
                && passwordResetTokenExpiresAt.isAfter(Instant.now());
    }

    public void resetPassword(String token, String newPassword, PasswordHasherPort hasher) {
        if (!isResetTokenValid(token)) {
            throw new IllegalArgumentException("Invalid or expired password reset token");
        }
        this.passwordHash = UserPassword.hash(newPassword, hasher);
        this.passwordResetToken = null;
        this.passwordResetTokenExpiresAt = null;
        this.failedLoginAttempts = 0;
        this.updatedAt = Instant.now();
    }

    public void recordSuccessfulLogin(Instant instant) {
        this.lastLoginAt = instant;
        this.failedLoginAttempts = 0;
        this.status = UserStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public boolean isLocked() {
        return status == UserStatus.LOCKED;
    }

    public boolean canLogin() {
        return active && status == UserStatus.ACTIVE && failedLoginAttempts < 5;
    }

    public String getId() { return id; }
    public Email getEmail() { return email; }
    public UserProfile getProfile() { return profile; }
    public String getFullName() { return profile.fullName(); }
    public boolean isActive() { return active; }
    public Set<Address> getAddresses() { return Set.copyOf(addresses); }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UserPassword getPasswordHash() { return passwordHash; }
    public String getPasswordResetToken() { return passwordResetToken; }
    public Instant getPasswordResetTokenExpiresAt() { return passwordResetTokenExpiresAt; }
}
