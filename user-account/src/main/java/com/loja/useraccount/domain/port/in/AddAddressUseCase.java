package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.Address;

/** Input port: adds an address to a user's address book. */
public interface AddAddressUseCase {
    Address addAddress(String userId, String street, String number, String complement,
                       String neighborhood, String city, String state,
                       String postalCode, String label, boolean setAsDefault);
}
