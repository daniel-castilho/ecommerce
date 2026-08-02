package com.loja.useraccount.domain.model;

import com.loja.shared.domain.Result;
import com.loja.useraccount.domain.validation.DomainError;
import java.util.Objects;
import java.util.regex.Pattern;

public final class Email {

    private static final Pattern REGEX =
        Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private final String value;

    public Email(String value) {
        if (value == null || !REGEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid e-mail: " + value);
        }
        this.value = value.toLowerCase();
    }

    public static Result<Email, DomainError> tryCreate(String value) {
        if (value == null || !REGEX.matcher(value).matches()) {
            return Result.failure(new DomainError.EmailError("Invalid e-mail: " + value));
        }
        return Result.success(new Email(value));
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email email)) return false;
        return value.equals(email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
