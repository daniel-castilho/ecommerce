package com.loja.useraccount.adapter.auth;

import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.in.ValidateCredentialsUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.CallerPrincipal;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStore;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Jakarta Security identity store backed by the user-account repository and the
 * Argon2id hasher. Validation is delegated to {@link ValidateCredentialsUseCase} so
 * the domain rules (account status, failed-attempt lockout) stay in the application
 * layer. Container groups mirror the role enum names, so {@code @RolesAllowed("ADMIN")}
 * and {@code SecurityContext.isCallerInRole(...)} resolve against real roles.
 */
@ApplicationScoped
public class UserIdentityStore implements IdentityStore {

    @Inject
    ValidateCredentialsUseCase validateCredentialsUseCase;

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Set<ValidationType> validationTypes() {
        return Set.of(ValidationType.VALIDATE, ValidationType.PROVIDE_GROUPS);
    }

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (!(credential instanceof UsernamePasswordCredential upc)) {
            return CredentialValidationResult.NOT_VALIDATED_RESULT;
        }
        return validateCredentialsUseCase.validateCredentials(
                        upc.getCaller(), upc.getPasswordAsString())
                .map(this::toResult)
                .orElse(CredentialValidationResult.INVALID_RESULT);
    }

    private CredentialValidationResult toResult(User user) {
        Set<String> groups = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toUnmodifiableSet());
        return new CredentialValidationResult(
                new CallerPrincipal(user.getEmail().getValue()), groups);
    }
}
