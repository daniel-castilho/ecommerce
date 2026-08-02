package com.loja.useraccount.domain.port.in;

/** Input port: removes an address from a user's address book. */
public interface DeleteAddressUseCase {
    void deleteAddress(String userId, Long addressId);
}
