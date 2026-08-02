package com.loja.useraccount.domain.model;

import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final PasswordHasherPort TEST_HASHER = new PasswordHasherPort() {
        @Override
        public String hash(String plainPassword) {
            return "hash:" + plainPassword;
        }

        @Override
        public boolean verify(String plainPassword, String hash) {
            return ("hash:" + plainPassword).equals(hash);
        }
    };

    private static UserPassword testPassword(String plain) {
        return UserPassword.hash(plain, TEST_HASHER);
    }

    private static UserProfile testProfile(String fullName) {
        return UserProfile.fromFullName(fullName);
    }

    @Test
    void shouldCreateUserWithActiveStatusAndDefaultRole() {
        User user = User.create(new Email("Client@Test.com"),
                testPassword("password1234"), testProfile("Maria Silva"));

        assertThat(user.getEmail().getValue()).isEqualTo("client@test.com");
        assertThat(user.getFullName()).isEqualTo("Maria Silva");
        assertThat(user.isActive()).isTrue();
        assertThat(user.getRoles()).contains(Role.CUSTOMER);
    }

    @Test
    void shouldUpdateProfileAndDeactivateUser() {
        User user = User.create(new Email("user@test.com"),
                testPassword("password1234"), testProfile("John Doe"));

        user.updateProfile(testProfile("John Smith"));
        user.deactivate();

        assertThat(user.getFullName()).isEqualTo("John Smith");
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> User.create(new Email("user@test.com"),
                testPassword("password1234"), UserProfile.fromFullName("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name");
    }

    @Test
    void shouldTrackLastLoginAndFailedAttempts() {
        User user = User.create(new Email("login@test.com"),
                testPassword("password1234"), testProfile("Ana"));

        user.recordLoginFailure();
        user.recordLoginFailure();
        user.recordLoginFailure();
        user.recordLoginFailure();
        user.recordLoginFailure();

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.isLocked()).isTrue();

        user.recordSuccessfulLogin(Instant.parse("2026-07-30T10:00:00Z"));
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(Instant.parse("2026-07-30T10:00:00Z"));
    }

    @Test
    void shouldAuthenticateAndChangePassword() {
        User user = User.create(new Email("auth@test.com"),
                testPassword("password1234"), testProfile("Bia"));

        assertThat(user.authenticate("password1234", TEST_HASHER)).isTrue();
        assertThat(user.authenticate("wrongPassword", TEST_HASHER)).isFalse();

        user.changePassword("password1234", "newPassword123", TEST_HASHER);
        assertThat(user.getPasswordHash().getHash()).isEqualTo("hash:newPassword123");
    }

    @Test
    void shouldManageRolesAndAddresses() {
        User user = User.create(new Email("roles@test.com"),
                testPassword("password1234"), testProfile("Carlos"));

        user.addRole(Role.ADMIN);
        assertThat(user.hasRole(Role.ADMIN)).isTrue();

        Address address = new Address(1L, "Flower Street", "123", "Apt 1", "Downtown",
                "New York", "NY", "10001-000", "Home", true);
        user.addAddress(address, true);

        assertThat(user.getAddresses()).contains(address);
        assertThat(user.getAddresses()).hasSize(1);

        Address defaultAddress = user.getAddresses().stream().findFirst().orElseThrow();
        assertThat(defaultAddress.isDefault()).isTrue();
    }

    @Test
    void shouldRemoveAddressOnlyWhenAnotherExists() {
        User user = User.create(new Email("address@test.com"),
                testPassword("password1234"), testProfile("Lia"));
        Address first  = new Address(1L, "Street A", "10", null, "Downtown", "Boston", "MA", "02101-000", "Home", true);
        Address second = new Address(2L, "Street B", "20", null, "Downtown", "Boston", "MA", "02101-000", "Work", false);

        user.addAddress(first, true);
        user.addAddress(second, false);
        user.removeAddress(1L);

        assertThat(user.getAddresses()).hasSize(1);
        assertThat(user.getAddresses()).contains(second);

        assertThatThrownBy(() -> user.removeAddress(2L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRequestPasswordResetAndValidateToken() {
        User user = User.create(new Email("reset@test.com"),
                testPassword("password1234"), testProfile("Elisa"));

        user.requestPasswordReset("reset-token-123");

        assertThat(user.getPasswordResetToken()).isEqualTo("reset-token-123");
        assertThat(user.getPasswordResetTokenExpiresAt()).isNotNull();
        assertThat(user.isResetTokenValid("reset-token-123")).isTrue();
        assertThat(user.isResetTokenValid("another-token")).isFalse();
        assertThat(user.isResetTokenValid(null)).isFalse();
    }

    @Test
    void shouldRejectBlankResetToken() {
        User user = User.create(new Email("blank@test.com"),
                testPassword("password1234"), testProfile("Fábio"));

        assertThatThrownBy(() -> user.requestPasswordReset("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    @Test
    void shouldResetPasswordWithValidToken() {
        User user = User.create(new Email("reset2@test.com"),
                testPassword("password1234"), testProfile("Gabi"));

        user.requestPasswordReset("reset-token-456");
        user.resetPassword("reset-token-456", "newPassword123", TEST_HASHER);

        assertThat(user.getPasswordHash().getHash()).isEqualTo("hash:newPassword123");
        assertThat(user.getPasswordResetToken()).isNull();
        assertThat(user.getPasswordResetTokenExpiresAt()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.isResetTokenValid("reset-token-456")).isFalse();
    }

    @Test
    void shouldRejectResetWithInvalidOrExpiredToken() {
        User user = User.create(new Email("reset3@test.com"),
                testPassword("password1234"), testProfile("Hugo"));

        user.requestPasswordReset("reset-token-789");

        assertThatThrownBy(() -> user.resetPassword("wrong-token", "newPassword123", TEST_HASHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");

        user.restorePasswordReset("reset-token-789", Instant.now().minusSeconds(3600));
        assertThat(user.isResetTokenValid("reset-token-789")).isFalse();
        assertThatThrownBy(() -> user.resetPassword("reset-token-789", "newPassword123", TEST_HASHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    @Test
    void shouldRestorePasswordResetState() {
        User user = User.create(new Email("restore@test.com"),
                testPassword("password1234"), testProfile("Iara"));
        Instant futureExpiry = Instant.now().plusSeconds(60 * 60);

        user.restorePasswordReset("restored-token", futureExpiry);

        assertThat(user.getPasswordResetToken()).isEqualTo("restored-token");
        assertThat(user.getPasswordResetTokenExpiresAt()).isEqualTo(futureExpiry);
        assertThat(user.isResetTokenValid("restored-token")).isTrue();
    }

    @Test
    void shouldSetDefaultAddress() {
        User user = User.create(new Email("default@test.com"),
                testPassword("password1234"), testProfile("Diana"));
        Address home = new Address(1L, "Home St", "1", null, "North", "NY", "NY", "10001-000", "Home", true);
        Address work = new Address(2L, "Work Ave", "100", null, "South", "NY", "NY", "10001-000", "Work", false);

        user.addAddress(home, true);
        user.addAddress(work, false);
        user.setDefaultAddress(2L);

        assertThat(user.getAddresses()).hasSize(2);
        assertThat(user.getAddresses().stream().filter(Address::isDefault)).hasSize(1);
        assertThat(user.getAddresses().stream().filter(Address::isDefault).findFirst().orElseThrow().getId()).isEqualTo(2L);
    }

    @Test
    void shouldAssignUniqueAddressIdsWhenNoneProvided() {
        User user = User.create(new Email("noid@test.com"),
                testPassword("password1234"), testProfile("Diana"));
        user.addAddress(new Address(null, "Home St", "1", null, "North", "NY", "NY", "10001-000", "Home", true), true);
        user.addAddress(new Address(null, "Work Ave", "100", null, "South", "NY", "NY", "10001-000", "Work", false), false);

        Address work = user.getAddresses().stream()
                .filter(a -> "Work".equals(a.getLabel()))
                .findFirst().orElseThrow();
        user.setDefaultAddress(work.getId());

        assertThat(user.getAddresses().stream().filter(Address::isDefault)).hasSize(1);
        assertThat(user.getAddresses().stream().filter(Address::isDefault)
                .findFirst().orElseThrow().getLabel()).isEqualTo("Work");
        assertThat(user.getAddresses().stream().map(Address::getId)).doesNotContainNull();
    }
}
