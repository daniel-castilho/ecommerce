package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.domain.model.Order;
import java.util.List;

public interface CheckoutUseCase {
    Order checkout(String userId, List<ItemCheckoutRequest> items);

    record ItemCheckoutRequest(String productId, int quantity) { }
}
