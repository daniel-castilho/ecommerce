package com.loja.ordercheckout.domain.model;

import java.util.regex.Pattern;

/**
 * Immutable delivery address value object. Required fields must be non-blank and
 * the postal code (CEP) must match the Brazilian XXXXX-XXX format.
 */
public final class ShippingAddress {

    private static final Pattern CEP = Pattern.compile("\\d{5}-?\\d{3}");

    private final String recipientName;
    private final String street;
    private final String number;
    private final String complement;
    private final String neighborhood;
    private final String city;
    private final String state;
    private final String postalCode;
    private final String phoneNumber;

    public ShippingAddress(String recipientName, String street, String number, String complement,
                           String neighborhood, String city, String state, String postalCode,
                           String phoneNumber) {
        this.recipientName = requireNotBlank(recipientName, "Recipient name");
        this.street = requireNotBlank(street, "Street");
        this.number = requireNotBlank(number, "Number");
        this.complement = complement;
        this.neighborhood = neighborhood;
        this.city = requireNotBlank(city, "City");
        this.state = requireNotBlank(state, "State");
        this.postalCode = requireCep(postalCode);
        this.phoneNumber = phoneNumber;
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String requireCep(String value) {
        String cep = requireNotBlank(value, "Postal code");
        if (!CEP.matcher(cep).matches()) {
            throw new IllegalArgumentException("Postal code must match XXXXX-XXX format");
        }
        return cep;
    }

    public String getRecipientName() { return recipientName; }
    public String getStreet() { return street; }
    public String getNumber() { return number; }
    public String getComplement() { return complement; }
    public String getNeighborhood() { return neighborhood; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getPhoneNumber() { return phoneNumber; }
}
