package com.loja.useraccount.domain.validation;

public sealed interface DomainError {
    String message();

    record EmailError(String message) implements DomainError {}
    record PasswordError(String message) implements DomainError {}
    record NameError(String message) implements DomainError {}
    record PhoneError(String message) implements DomainError {}
    record AddressError(String message) implements DomainError {}
    record PostalCodeError(String message) implements DomainError {}
    record UserNotFound(String message) implements DomainError {}
    record EmailAlreadyTaken(String message) implements DomainError {}
    record PasswordMismatch(String message) implements DomainError {}
    record AccountLocked(String message) implements DomainError {}
    record AddressNotFound(String message) implements DomainError {}
    record CannotRemoveLastAddress(String message) implements DomainError {}
    record RoleAssignmentError(String message) implements DomainError {}
    record GeneralError(String message) implements DomainError {}
}
