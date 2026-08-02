package com.loja.useraccount.domain.model;

import com.loja.shared.domain.Result;
import com.loja.useraccount.domain.validation.DomainError;
import java.util.Objects;

public final class Address {

    private final Long id;
    private final String street;
    private final String number;
    private final String complement;
    private final String neighborhood;
    private final String city;
    private final String state;
    private final String postalCode;
    private final String label;
    private final boolean isDefault;

    public Address(Long id, String street, String number, String complement, String neighborhood,
                   String city, String state, String postalCode, String label, boolean isDefault) {
        if (street == null || street.isBlank()) {
            throw new IllegalArgumentException("Street required");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City required");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("State required");
        }
        if (postalCode == null || !postalCode.matches("^\\d{5}-?\\d{3}$")) {
            throw new IllegalArgumentException("Invalid CEP format");
        }
        this.id = id;
        this.street = street;
        this.number = number;
        this.complement = complement;
        this.neighborhood = neighborhood;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.label = label;
        this.isDefault = isDefault;
    }

    public static Result<Address, DomainError> tryCreate(Long id, String street, String number,
                                                          String complement, String neighborhood,
                                                          String city, String state,
                                                          String postalCode, String label,
                                                          boolean isDefault) {
        if (street == null || street.isBlank()) {
            return Result.failure(new DomainError.AddressError("Street required"));
        }
        if (city == null || city.isBlank()) {
            return Result.failure(new DomainError.AddressError("City required"));
        }
        if (state == null || state.isBlank()) {
            return Result.failure(new DomainError.AddressError("State required"));
        }
        if (postalCode == null || !postalCode.matches("^\\d{5}-?\\d{3}$")) {
            return Result.failure(new DomainError.PostalCodeError("Invalid CEP format"));
        }
        return Result.success(new Address(id, street, number, complement, neighborhood,
                city, state, postalCode, label, isDefault));
    }

    public Long getId() { return id; }
    public String getStreet() { return street; }
    public String getNumber() { return number; }
    public String getComplement() { return complement; }
    public String getNeighborhood() { return neighborhood; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getLabel() { return label; }
    public boolean isDefault() { return isDefault; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address address)) return false;
        return isDefault == address.isDefault && Objects.equals(id, address.id) && Objects.equals(street, address.street)
            && Objects.equals(number, address.number) && Objects.equals(complement, address.complement)
            && Objects.equals(neighborhood, address.neighborhood) && Objects.equals(city, address.city)
            && Objects.equals(state, address.state) && Objects.equals(postalCode, address.postalCode)
            && Objects.equals(label, address.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, street, number, complement, neighborhood, city, state, postalCode, label, isDefault);
    }
}
