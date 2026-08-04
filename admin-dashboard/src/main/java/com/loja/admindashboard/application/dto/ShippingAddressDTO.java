package com.loja.admindashboard.application.dto;

import java.util.Objects;

/**
 * Presentation-friendly shipping address used by admin order detail views.
 */
public final class ShippingAddressDTO {

    private final String recipientName;
    private final String street;
    private final String number;
    private final String complement;
    private final String neighborhood;
    private final String city;
    private final String state;
    private final String postalCode;

    public ShippingAddressDTO(
            String recipientName,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String postalCode) {
        this.recipientName = recipientName;
        this.street = street;
        this.number = number;
        this.complement = complement;
        this.neighborhood = neighborhood;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getComplement() {
        return complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShippingAddressDTO that = (ShippingAddressDTO) o;
        return Objects.equals(recipientName, that.recipientName)
                && Objects.equals(street, that.street)
                && Objects.equals(number, that.number)
                && Objects.equals(complement, that.complement)
                && Objects.equals(neighborhood, that.neighborhood)
                && Objects.equals(city, that.city)
                && Objects.equals(state, that.state)
                && Objects.equals(postalCode, that.postalCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipientName, street, number, complement, neighborhood, city, state, postalCode);
    }
}
