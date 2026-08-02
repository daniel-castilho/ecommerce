package com.loja.useraccount.domain.validation;

import com.loja.shared.domain.Result;
import com.loja.useraccount.domain.model.Address;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationTest {

    private static final PasswordHasherPort TEST_HASHER = new PasswordHasherPort() {
        @Override public String hash(String plainPassword) { return "argon2:" + plainPassword; }
        @Override public boolean verify(String plainPassword, String hash) {
            return ("argon2:" + plainPassword).equals(hash);
        }
    };

    @Test
    void resultShouldWrapSuccess() {
        Result<String, String> result = Result.success("ok");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFailure()).isFalse();
        assertThat(result.getValue()).contains("ok");
        assertThat(result.orElseThrow()).isEqualTo("ok");
    }

    @Test
    void resultShouldWrapFailure() {
        Result<String, String> result = Result.failure("error");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).contains("error");
    }

    @Test
    void resultMapShouldTransformSuccessValue() {
        Result<Integer, String> result = Result.success(42);
        Result<String, String> mapped = result.map(Object::toString);
        assertThat(mapped.getValue()).contains("42");
    }

    @Test
    void resultMapShouldPropagateFailure() {
        Result<Integer, String> result = Result.failure("err");
        Result<String, String> mapped = result.map(Object::toString);
        assertThat(mapped.getError()).contains("err");
    }

    @Test
    void resultFlatMapShouldChainSuccesses() {
        Result<Integer, String> result = Result.success(10);
        Result<Integer, String> chained = result.flatMap(v -> Result.success(v * 2));
        assertThat(chained.getValue()).contains(20);
    }

    @Test
    void resultIfSuccessShouldExecuteConsumer() {
        AtomicReference<String> captured = new AtomicReference<>();
        Result<String, String> result = Result.success("hello");
        result.ifSuccess(captured::set);
        assertThat(captured.get()).isEqualTo("hello");
    }

    @Test
    void resultIfFailureShouldExecuteConsumer() {
        AtomicReference<String> captured = new AtomicReference<>();
        Result<String, String> result = Result.failure("fail");
        result.ifFailure(captured::set);
        assertThat(captured.get()).isEqualTo("fail");
    }

    @Test
    void emailTryCreateShouldAcceptValidEmail() {
        var result = Email.tryCreate("user@example.com");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().get().getValue()).isEqualTo("user@example.com");
    }

    @Test
    void emailTryCreateShouldRejectInvalidEmail() {
        var result = Email.tryCreate("not-an-email");
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().get()).isInstanceOf(DomainError.EmailError.class);
    }

    @Test
    void emailTryCreateShouldRejectNullEmail() {
        var result = Email.tryCreate(null);
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void userPasswordTryHashShouldAcceptValidPassword() {
        var result = UserPassword.tryHash("Password1", TEST_HASHER);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void userPasswordTryHashShouldRejectShortPassword() {
        var result = UserPassword.tryHash("Ab1", TEST_HASHER);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().get()).isInstanceOf(DomainError.PasswordError.class);
    }

    @Test
    void userPasswordTryFromHashShouldAcceptValidHash() {
        var result = UserPassword.tryFromHash("argon2:abc123");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().get().getHash()).isEqualTo("argon2:abc123");
    }

    @Test
    void userPasswordTryFromHashShouldRejectEmptyHash() {
        var result = UserPassword.tryFromHash("");
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void userProfileTryFromFullNameShouldAcceptValidName() {
        var result = UserProfile.tryFromFullName("John Doe");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().get().fullName()).isEqualTo("John Doe");
    }

    @Test
    void userProfileTryFromFullNameShouldRejectBlankName() {
        var result = UserProfile.tryFromFullName("");
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().get()).isInstanceOf(DomainError.NameError.class);
    }

    @Test
    void userProfileTryCreateShouldAcceptValidFields() {
        var result = UserProfile.tryCreate("Jane", "Doe", "+5511999999999", "pt-BR", true);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void userProfileTryCreateShouldRejectBlankFirstName() {
        var result = UserProfile.tryCreate("", "Doe", null, "en", true);
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void addressTryCreateShouldAcceptValidAddress() {
        var result = Address.tryCreate(null, "Rua A", "100", null, "Centro",
                "São Paulo", "SP", "01001-000", "Home", false);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void addressTryCreateShouldRejectMissingStreet() {
        var result = Address.tryCreate(null, null, "100", null, "Centro",
                "São Paulo", "SP", "01001-000", "Home", false);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().get()).isInstanceOf(DomainError.AddressError.class);
    }

    @Test
    void addressTryCreateShouldRejectInvalidPostalCode() {
        var result = Address.tryCreate(null, "Rua A", "100", null, "Centro",
                "São Paulo", "SP", "123", "Home", false);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().get()).isInstanceOf(DomainError.PostalCodeError.class);
    }

    @Test
    void userTryRegisterShouldAcceptValidInputs() {
        var result = User.tryRegister("user@example.com", "Password1", "John Doe", TEST_HASHER);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().get().getEmail().getValue()).isEqualTo("user@example.com");
    }

    @Test
    void userTryRegisterShouldRejectInvalidEmail() {
        var result = User.tryRegister("invalid", "Password1", "John Doe", TEST_HASHER);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().get()).isInstanceOf(DomainError.EmailError.class);
    }

    @Test
    void userTryRegisterShouldRejectShortPassword() {
        var result = User.tryRegister("user@example.com", "Ab1", "John Doe", TEST_HASHER);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().get()).isInstanceOf(DomainError.PasswordError.class);
    }

    @Test
    void userTryRegisterShouldRejectBlankName() {
        var result = User.tryRegister("user@example.com", "Password1", "", TEST_HASHER);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().get()).isInstanceOf(DomainError.NameError.class);
    }
}
