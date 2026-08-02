package com.loja.useraccount.domain.model;

import com.loja.shared.domain.Result;
import com.loja.useraccount.domain.validation.DomainError;
import java.util.Objects;

public final class UserProfile {

    private final String firstName;
    private final String lastName;
    private final String phoneNumber;
    private final String preferredLanguage;
    private final boolean notificationsEnabled;

    public UserProfile(String firstName, String lastName, String phoneNumber,
                       String preferredLanguage, boolean notificationsEnabled) {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.phoneNumber = phoneNumber;
        this.preferredLanguage = preferredLanguage != null ? preferredLanguage : "en";
        this.notificationsEnabled = notificationsEnabled;
    }

    public static UserProfile fromFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        String trimmed = fullName.trim();
        int idx = trimmed.indexOf(' ');
        if (idx == -1) {
            return new UserProfile(trimmed, trimmed, null, "en", true);
        }
        return new UserProfile(trimmed.substring(0, idx), trimmed.substring(idx + 1).trim(),
                null, "en", true);
    }

    public static Result<UserProfile, DomainError> tryFromFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return Result.failure(new DomainError.NameError("Name cannot be blank"));
        }
        String trimmed = fullName.trim();
        int idx = trimmed.indexOf(' ');
        if (idx == -1) {
            return Result.success(new UserProfile(trimmed, trimmed, null, "en", true));
        }
        return Result.success(new UserProfile(trimmed.substring(0, idx), trimmed.substring(idx + 1).trim(),
                null, "en", true));
    }

    public static Result<UserProfile, DomainError> tryCreate(String firstName, String lastName,
                                                               String phoneNumber, String preferredLanguage,
                                                               boolean notificationsEnabled) {
        if (firstName == null || firstName.isBlank()) {
            return Result.failure(new DomainError.NameError("First name is required"));
        }
        if (lastName == null || lastName.isBlank()) {
            return Result.failure(new DomainError.NameError("Last name is required"));
        }
        return Result.success(new UserProfile(firstName.trim(), lastName.trim(),
                phoneNumber, preferredLanguage, notificationsEnabled));
    }

    public String fullName() {
        return firstName.equals(lastName) ? firstName : firstName + " " + lastName;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfile that)) return false;
        return notificationsEnabled == that.notificationsEnabled
                && firstName.equals(that.firstName)
                && lastName.equals(that.lastName)
                && Objects.equals(phoneNumber, that.phoneNumber)
                && Objects.equals(preferredLanguage, that.preferredLanguage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, phoneNumber, preferredLanguage, notificationsEnabled);
    }
}
