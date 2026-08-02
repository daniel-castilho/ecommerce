package com.loja.useraccount.domain.port.in;

/** Input port: sets a specific address as the default for a user. */
public interface SetDefaultAddressUseCase {
    void setDefaultAddress(String userId, Long addressId);
}
