package com.loja.useraccount.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileTest {

    @Test
    void shouldCreateProfileWithAllFields() {
        UserProfile profile = new UserProfile("John", "Doe", "+55 11 99999-0000", "en", true);
        assertThat(profile.getFirstName()).isEqualTo("John");
        assertThat(profile.getLastName()).isEqualTo("Doe");
        assertThat(profile.fullName()).isEqualTo("John Doe");
        assertThat(profile.getPhoneNumber()).isEqualTo("+55 11 99999-0000");
        assertThat(profile.getPreferredLanguage()).isEqualTo("en");
        assertThat(profile.isNotificationsEnabled()).isTrue();
    }

    @Test
    void shouldDefaultLanguageToEnglishWhenNull() {
        UserProfile profile = new UserProfile("Jane", "Doe", null, null, false);
        assertThat(profile.getPreferredLanguage()).isEqualTo("en");
    }

    @Test
    void shouldRejectEmptyFirstName() {
        assertThatThrownBy(() -> new UserProfile("", "Doe", null, "en", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("First name");
    }

    @Test
    void shouldRejectEmptyLastName() {
        assertThatThrownBy(() -> new UserProfile("Jane", "", null, "en", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Last name");
    }

    @Test
    void shouldBuildProfileFromFullName() {
        UserProfile profile = UserProfile.fromFullName("  Maria Silva  ");
        assertThat(profile.getFirstName()).isEqualTo("Maria");
        assertThat(profile.getLastName()).isEqualTo("Silva");
        assertThat(profile.fullName()).isEqualTo("Maria Silva");
    }

    @Test
    void shouldHandleSingleWordName() {
        UserProfile profile = UserProfile.fromFullName("Madonna");
        assertThat(profile.getFirstName()).isEqualTo("Madonna");
        assertThat(profile.getLastName()).isEqualTo("Madonna");
        assertThat(profile.fullName()).isEqualTo("Madonna");
    }

    @Test
    void shouldRejectBlankFullName() {
        assertThatThrownBy(() -> UserProfile.fromFullName("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        UserProfile a = new UserProfile("John", "Doe", null, "en", true);
        UserProfile b = new UserProfile("John", "Doe", null, "en", true);
        UserProfile c = new UserProfile("Jane", "Doe", null, "en", true);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
