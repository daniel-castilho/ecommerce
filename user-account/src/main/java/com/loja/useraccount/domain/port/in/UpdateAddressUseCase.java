package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.Address;

/** Input port: updates an existing address in a user's address book. */
public interface UpdateAddressUseCase {
    Address updateAddress(String userId, Long addressId, String street, String number,
                          String complement, String neighborhood, String city,
                          String state, String postalCode, String label, boolean setAsDefault);
}
