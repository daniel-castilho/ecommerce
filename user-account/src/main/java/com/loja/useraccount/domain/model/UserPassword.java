package com.loja.useraccount.domain.model;

import com.loja.shared.domain.Result;
import com.loja.useraccount.domain.validation.DomainError;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import java.util.Objects;

public final class UserPassword {

    private final String hash;

    private UserPassword(String hash) {
        this.hash = hash;
    }

    public static UserPassword hash(String plainPassword, PasswordHasherPort hasher) {
        if (plainPassword == null || plainPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        return new UserPassword(hasher.hash(plainPassword));
    }

    public static Result<UserPassword, DomainError> tryHash(String plainPassword, PasswordHasherPort hasher) {
        if (plainPassword == null || plainPassword.length() < 8) {
            return Result.failure(new DomainError.PasswordError("Password must be at least 8 characters"));
        }
        return Result.success(new UserPassword(hasher.hash(plainPassword)));
    }

    public static UserPassword fromHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Hash cannot be empty");
        }
        return new UserPassword(hash);
    }

    public static Result<UserPassword, DomainError> tryFromHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return Result.failure(new DomainError.PasswordError("Hash cannot be empty"));
        }
        return Result.success(new UserPassword(hash));
    }

    public boolean matches(String plainPassword, PasswordHasherPort hasher) {
        return hasher.verify(plainPassword, this.hash);
    }

    public String getHash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPassword that)) return false;
        return hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }
}
