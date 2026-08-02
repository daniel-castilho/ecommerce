package com.loja.useraccount.adapter.in.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the Bean Validation constraints on the JSF adapter beans.
 * The constraints must mirror the domain rules (Email, UserPassword min length,
 * Address CEP/state) without any business logic living in the adapter.
 */
class BeanValidationConstraintsTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void registerBean_invalidEmail_hasEmailViolation() {
        RegisterBean bean = new RegisterBean();
        bean.setFullName("Valid Name");
        bean.setEmail("not-an-email");
        bean.setPassword("StrongPass@1");

        Set<ConstraintViolation<RegisterBean>> violations = validator.validate(bean);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder("Invalid e-mail format");
    }

    @Test
    void registerBean_shortPassword_hasPasswordViolation() {
        RegisterBean bean = new RegisterBean();
        bean.setFullName("Valid Name");
        bean.setEmail("user@loja.com");
        bean.setPassword("abc");

        Set<ConstraintViolation<RegisterBean>> violations = validator.validate(bean);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("Password must be between 8 and 72 characters");
    }

    @Test
    void registerBean_blankName_hasNameViolation() {
        RegisterBean bean = new RegisterBean();
        bean.setFullName("   ");
        bean.setEmail("user@loja.com");
        bean.setPassword("StrongPass@1");

        Set<ConstraintViolation<RegisterBean>> violations = validator.validate(bean);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("Full name is required");
    }

    @Test
    void registerBean_validInput_hasNoViolations() {
        RegisterBean bean = new RegisterBean();
        bean.setFullName("Valid Name");
        bean.setEmail("user@loja.com");
        bean.setPassword("StrongPass@1");

        Set<ConstraintViolation<RegisterBean>> violations = validator.validate(bean);

        assertThat(violations).isEmpty();
    }

    @Test
    void loginBean_invalidEmail_hasEmailViolation() {
        LoginBean bean = new LoginBean();
        bean.setEmail("bad-email");
        bean.setPassword("anything");

        Set<ConstraintViolation<LoginBean>> violations = validator.validate(bean);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder("Invalid e-mail format");
    }

    @Test
    void addressBookBean_badStateAndPostalCode_hasBothViolations() {
        AddressBookBean bean = new AddressBookBean();
        bean.setStreet("Av Teste 123");
        bean.setCity("Sao Paulo");
        bean.setState("S");
        bean.setPostalCode("12X45");

        Set<ConstraintViolation<AddressBookBean>> violations = validator.validate(bean);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder("State must have exactly 2 characters",
                        "Invalid postal code format");
    }

    @Test
    void addressBookBean_missingRequiredFields_hasViolations() {
        AddressBookBean bean = new AddressBookBean();

        Set<ConstraintViolation<AddressBookBean>> violations = validator.validate(bean);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .containsExactlyInAnyOrder("street", "city", "state", "postalCode");
    }

    @Test
    void addressBookBean_validAddress_hasNoViolations() {
        AddressBookBean bean = new AddressBookBean();
        bean.setStreet("Av Teste 123");
        bean.setCity("Sao Paulo");
        bean.setState("SP");
        bean.setPostalCode("01310-100");

        Set<ConstraintViolation<AddressBookBean>> violations = validator.validate(bean);

        assertThat(violations).isEmpty();
    }

    @Test
    void profileBean_shortNewPassword_hasViolation() {
        ProfileBean bean = new ProfileBean();
        bean.setFullName("Valid Name");
        bean.setCurrentPassword("OldPass@1");
        bean.setNewPassword("short");

        Set<ConstraintViolation<ProfileBean>> violations = validator.validate(bean);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("Password must be between 8 and 72 characters");
    }

    @Test
    void passwordResetBean_invalidEmail_hasEmailViolation() {
        PasswordResetBean bean = new PasswordResetBean();
        bean.setEmail("bad");
        bean.setNewPassword("StrongPass@1");

        Set<ConstraintViolation<PasswordResetBean>> violations = validator.validate(bean);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder("Invalid e-mail format");
    }
}
