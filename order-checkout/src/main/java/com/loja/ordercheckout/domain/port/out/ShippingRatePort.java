package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.domain.exception.ShippingException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.model.ShippingLabel;
import com.loja.ordercheckout.domain.model.ShippingOption;
import java.util.List;

/**
 * Outbound port for shipping rates and label generation. Implementations wrap a
 * real carrier (Correios) or a local mock.
 */
public interface ShippingRatePort {

    /**
     * Quotes delivery options for a destination address.
     *
     * @param address the delivery destination; may be validated by the real adapter
     * @return at least one {@link ShippingOption}, never {@code null}
     * @throws ShippingException if the quote cannot be produced
     */
    List<ShippingOption> getQuotes(ShippingAddress address) throws ShippingException;

    /**
     * Creates a shipping label for an order using the selected delivery option.
     *
     * @param order    the order being shipped
     * @param selected the option returned by {@link #getQuotes} that the customer chose
     * @return the label, including a non-empty carrier-formatted {@code trackingNumber}
     * @throws ShippingException if the method is unavailable or the label fails
     */
    ShippingLabel createLabel(Order order, ShippingOption selected) throws ShippingException;
}
