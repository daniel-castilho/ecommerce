package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.domain.model.Address;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.model.UserStatus;
import jakarta.persistence.Cacheable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA persistence entity for User.
 * Isolated in the adapter layer — the domain object (User) never carries
 * framework annotations, avoiding an anemic domain model coupled to infrastructure.
 * To swap persistence technology, write a new adapter implementing UserRepositoryPort;
 * the domain stays untouched (Open/Closed Principle).
 */
@Entity
@Table(name = "user_account")
@Cacheable(false)
public class UserJpaEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 500)
    private String passwordHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_reset_token", length = 500)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expires_at")
    private Instant passwordResetTokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 50)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_address", joinColumns = @JoinColumn(name = "user_id"))
    private Set<AddressEmbeddable> addresses = new HashSet<>();

    /** Required by JPA. */
    protected UserJpaEntity() {}

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public UserStatus getStatus() { return status; }
    public boolean isActive() { return active; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public String getPasswordResetToken() { return passwordResetToken; }
    public Instant getPasswordResetTokenExpiresAt() { return passwordResetTokenExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Role> getRoles() { return roles; }
    public Set<AddressEmbeddable> getAddresses() { return addresses; }

    // -------------------------------------------------------------------------
    // Mapping: domain → entity
    // -------------------------------------------------------------------------

    public static UserJpaEntity fromDomain(User user) {
        UserJpaEntity e = new UserJpaEntity();
        e.id = user.getId();
        e.email = user.getEmail().getValue();
        e.fullName = user.getFullName();
        e.passwordHash = user.getPasswordHash().getHash();
        e.status = user.getStatus();
        e.active = user.isActive();
        e.failedLoginAttempts = user.getFailedLoginAttempts();
        e.lastLoginAt = user.getLastLoginAt();
        e.passwordResetToken = user.getPasswordResetToken();
        e.passwordResetTokenExpiresAt = user.getPasswordResetTokenExpiresAt();
        e.createdAt = user.getCreatedAt();
        e.updatedAt = user.getUpdatedAt();
        e.roles = new HashSet<>(user.getRoles());
        e.addresses = user.getAddresses().stream()
                .map(AddressEmbeddable::fromDomain)
                .collect(Collectors.toCollection(HashSet::new));
        return e;
    }

    // -------------------------------------------------------------------------
    // Mapping: entity → domain
    // -------------------------------------------------------------------------

    public User toDomain() {
        User user = new User(id, new Email(email),
                UserPassword.fromHash(passwordHash),
                UserProfile.fromFullName(fullName));
        roles.forEach(user::addRole);
        addresses.stream()
                .map(AddressEmbeddable::toDomain)
                .forEach(addr -> user.addAddress(addr, addr.isDefault()));
        if (status == UserStatus.INACTIVE) {
            user.deactivate();
        } else if (status == UserStatus.LOCKED) {
            for (int i = 0; i < failedLoginAttempts; i++) {
                user.recordLoginFailure();
            }
        }
        if (lastLoginAt != null) {
            user.recordSuccessfulLogin(lastLoginAt);
        }
        if (passwordResetToken != null && passwordResetTokenExpiresAt != null) {
            user.restorePasswordReset(passwordResetToken, passwordResetTokenExpiresAt);
        }
        return user;
    }

    // -------------------------------------------------------------------------
    // Embeddable for Address
    // -------------------------------------------------------------------------

    @Embeddable
    public static class AddressEmbeddable {

        @Column(name = "address_id")
        private Long addressId;

        @Column(nullable = false, length = 255)
        private String street;

        @Column(length = 20)
        private String number;

        @Column(length = 100)
        private String complement;

        @Column(length = 100)
        private String neighborhood;

        @Column(nullable = false, length = 100)
        private String city;

        @Column(nullable = false, length = 2)
        private String state;

        @Column(name = "postal_code", nullable = false, length = 10)
        private String postalCode;

        @Column(length = 50)
        private String label;

        @Column(name = "is_default", nullable = false)
        private boolean isDefault;

        /** Required by JPA. */
        protected AddressEmbeddable() {}

        public static AddressEmbeddable fromDomain(Address address) {
            AddressEmbeddable e = new AddressEmbeddable();
            e.addressId = address.getId();
            e.street = address.getStreet();
            e.number = address.getNumber();
            e.complement = address.getComplement();
            e.neighborhood = address.getNeighborhood();
            e.city = address.getCity();
            e.state = address.getState();
            e.postalCode = address.getPostalCode();
            e.label = address.getLabel();
            e.isDefault = address.isDefault();
            return e;
        }

        public Address toDomain() {
            return new Address(addressId, street, number, complement, neighborhood,
                    city, state, postalCode, label, isDefault);
        }

        public boolean isDefault() { return isDefault; }
    }
}
