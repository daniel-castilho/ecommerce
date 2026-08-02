package com.loja.useraccount.domain.model;

import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPasswordTest {

    private static final PasswordHasherPort TEST_HASHER = new PasswordHasherPort() {
        @Override
        public String hash(String plainPassword) {
            return "argon2:" + plainPassword;
        }

        @Override
        public boolean verify(String plainPassword, String hash) {
            return ("argon2:" + plainPassword).equals(hash);
        }
    };

    @Test
    void shouldHashPasswordViaFactory() {
        UserPassword password = UserPassword.hash("validPassword1", TEST_HASHER);
        assertThat(password.getHash()).isEqualTo("argon2:validPassword1");
    }

    @Test
    void shouldRejectPasswordShorterThan8Chars() {
        assertThatThrownBy(() -> UserPassword.hash("short", TEST_HASHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8");
    }

    @Test
    void shouldVerifyCorrectPassword() {
        UserPassword password = UserPassword.hash("correctPassword", TEST_HASHER);
        assertThat(password.matches("correctPassword", TEST_HASHER)).isTrue();
    }

    @Test
    void shouldRejectWrongPassword() {
        UserPassword password = UserPassword.hash("correctPassword", TEST_HASHER);
        assertThat(password.matches("wrongPassword", TEST_HASHER)).isFalse();
    }

    @Test
    void shouldRestoreFromExistingHash() {
        UserPassword password = UserPassword.fromHash("argon2:existingHash");
        assertThat(password.getHash()).isEqualTo("argon2:existingHash");
    }

    @Test
    void shouldRejectEmptyHashOnRestore() {
        assertThatThrownBy(() -> UserPassword.fromHash(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        UserPassword a = UserPassword.fromHash("hash-abc");
        UserPassword b = UserPassword.fromHash("hash-abc");
        UserPassword c = UserPassword.fromHash("hash-xyz");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isNotEqualTo(c.hashCode());
    }
}
