package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.adapter.out.persistence.UserJpaEntity.AddressEmbeddable;
import com.loja.useraccount.domain.model.Address;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.model.UserStatus;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserJpaMapperTest {

    private static final PasswordHasherPort TEST_HASHER = new PasswordHasherPort() {
        @Override public String hash(String plainPassword) { return "argon2:" + plainPassword; }
        @Override public boolean verify(String plainPassword, String hash) {
            return ("argon2:" + plainPassword).equals(hash);
        }
    };

    @Test
    void shouldRoundTripDomainToEntityAndBack() {
        User original = User.create(
                new Email("test@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("John Doe")
        );
        Address addr = new Address(null, "Rua A", "100", null, "Centro",
                "São Paulo", "SP", "01001-000", "Home", false);
        original.addAddress(addr, false);
        original.addRole(Role.ADMIN);

        UserJpaEntity entity = UserJpaEntity.fromDomain(original);
        User restored = entity.toDomain();

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getEmail().getValue()).isEqualTo("test@example.com");
        assertThat(restored.getFullName()).isEqualTo("John Doe");
        assertThat(restored.getPasswordHash().getHash()).isEqualTo("argon2:Password1");
        assertThat(restored.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(restored.isActive()).isTrue();
        assertThat(restored.getRoles()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.ADMIN);
        assertThat(restored.getAddresses()).hasSize(1);
        assertThat(restored.getAddresses().iterator().next().getStreet()).isEqualTo("Rua A");
    }

    @Test
    void shouldMapNullLastLoginAt() {
        User user = User.create(
                new Email("null-login@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Null Login")
        );

        UserJpaEntity entity = UserJpaEntity.fromDomain(user);

        assertThat(entity.getLastLoginAt()).isNull();
    }

    @Test
    void shouldMapLockedUserStatus() {
        User user = User.create(
                new Email("locked@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Locked User")
        );
        for (int i = 0; i < 5; i++) {
            user.recordLoginFailure();
        }

        UserJpaEntity entity = UserJpaEntity.fromDomain(user);
        User restored = entity.toDomain();

        assertThat(restored.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(restored.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    void shouldMapInactiveUser() {
        User user = User.create(
                new Email("inactive@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Inactive User")
        );
        user.deactivate();

        UserJpaEntity entity = UserJpaEntity.fromDomain(user);
        User restored = entity.toDomain();

        assertThat(restored.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(restored.isActive()).isFalse();
    }

    @Test
    void shouldMapAddressEmbeddableRoundTrip() {
        Address original = new Address(42L, "Av Paulista", "1000", "Apto 42",
                "Bela Vista", "São Paulo", "SP", "01310-100", "Office", true);

        AddressEmbeddable embeddable = AddressEmbeddable.fromDomain(original);
        Address restored = embeddable.toDomain();

        assertThat(restored.getId()).isEqualTo(42L);
        assertThat(restored.getStreet()).isEqualTo("Av Paulista");
        assertThat(restored.getNumber()).isEqualTo("1000");
        assertThat(restored.getComplement()).isEqualTo("Apto 42");
        assertThat(restored.getNeighborhood()).isEqualTo("Bela Vista");
        assertThat(restored.getCity()).isEqualTo("São Paulo");
        assertThat(restored.getState()).isEqualTo("SP");
        assertThat(restored.getPostalCode()).isEqualTo("01310-100");
        assertThat(restored.getLabel()).isEqualTo("Office");
        assertThat(restored.isDefault()).isTrue();
    }

    @Test
    void shouldPreserveLastLoginInRoundTrip() {
        User user = User.create(
                new Email("login-ts@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Login Timestamp")
        );
        Instant loginTime = Instant.parse("2025-06-15T10:30:00Z");
        user.recordSuccessfulLogin(loginTime);

        UserJpaEntity entity = UserJpaEntity.fromDomain(user);
        User restored = entity.toDomain();

        assertThat(restored.getLastLoginAt()).isEqualTo(loginTime);
        assertThat(restored.getFailedLoginAttempts()).isZero();
    }
}
