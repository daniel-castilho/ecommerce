package com.loja.useraccount.adapter.auth;

import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.in.ValidateCredentialsUseCase;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserIdentityStoreTest {

    private final ValidateCredentialsUseCase validateCredentials = mock(ValidateCredentialsUseCase.class);

    private UserIdentityStore identityStore;

    @BeforeEach
    void setUp() {
        identityStore = new UserIdentityStore();
        identityStore.validateCredentialsUseCase = validateCredentials;
    }

    @Test
    void validate_validCredentials_returnsValidResultWithRolesAsGroups() {
        User admin = User.create(new Email("admin@example.com"),
                UserPassword.hash("password1234", TestHasher.INSTANCE),
                UserProfile.fromFullName("Admin User"));
        admin.addRole(Role.ADMIN);
        when(validateCredentials.validateCredentials("admin@example.com", "password1234"))
                .thenReturn(Optional.of(admin));

        CredentialValidationResult result = identityStore.validate(
                new UsernamePasswordCredential("admin@example.com", "password1234"));

        assertThat(result.getStatus()).isEqualTo(CredentialValidationResult.Status.VALID);
        assertThat(result.getCallerPrincipal().getName()).isEqualTo("admin@example.com");
        assertThat(result.getCallerGroups()).containsExactlyInAnyOrder(
                Role.CUSTOMER.name(), Role.ADMIN.name());
    }

    @Test
    void validate_invalidCredentials_returnsInvalidResult() {
        when(validateCredentials.validateCredentials("user@example.com", "wrong"))
                .thenReturn(Optional.empty());

        CredentialValidationResult result = identityStore.validate(
                new UsernamePasswordCredential("user@example.com", "wrong"));

        assertThat(result.getStatus()).isEqualTo(CredentialValidationResult.Status.INVALID);
    }

    @Test
    void validate_unknownCredentialType_returnsNotValidated() {
        CredentialValidationResult result = identityStore.validate(new Credential() { });

        assertThat(result.getStatus()).isEqualTo(CredentialValidationResult.Status.NOT_VALIDATED);
    }

    /** Minimal hasher so the created user verifies (used only to build the fixture). */
    private enum TestHasher implements com.loja.useraccount.domain.port.out.PasswordHasherPort {
        INSTANCE;

        @Override
        public String hash(String plainPassword) {
            return "hash:" + plainPassword;
        }

        @Override
        public boolean verify(String plainPassword, String hash) {
            return ("hash:" + plainPassword).equals(hash);
        }
    }
}
