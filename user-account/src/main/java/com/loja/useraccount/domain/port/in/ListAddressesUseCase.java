package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.domain.model.Address;
import java.util.Set;

/** Input port: lists all addresses for a given user. */
public interface ListAddressesUseCase {
    Set<Address> listAddresses(String userId);
}
