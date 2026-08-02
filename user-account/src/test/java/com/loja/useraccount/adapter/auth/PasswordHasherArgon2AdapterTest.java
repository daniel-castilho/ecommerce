package com.loja.useraccount.adapter.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHasherArgon2AdapterTest {

    private final PasswordHasherArgon2Adapter hasher = new PasswordHasherArgon2Adapter();

    @Test
    void shouldHashAndVerifyPassword() {
        String hash = hasher.hash("securePassword123");

        assertThat(hash).isNotBlank();
        assertThat(hash).isNotEqualTo("securePassword123");
        assertThat(hasher.verify("securePassword123", hash)).isTrue();
        assertThat(hasher.verify("wrongPassword", hash)).isFalse();
    }

    @Test
    void shouldProduceDifferentHashesForSamePassword() {
        String first  = hasher.hash("securePassword123");
        String second = hasher.hash("securePassword123");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldRejectBlankPassword() {
        assertThatThrownBy(() -> hasher.hash("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
