package com.loja.ordercheckout.adapter.out.shipping;

import com.loja.ordercheckout.domain.exception.ShippingException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.model.ShippingLabel;
import com.loja.ordercheckout.domain.model.ShippingOption;
import com.loja.ordercheckout.domain.port.out.ShippingRatePort;
import com.loja.shared.domain.Money;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * FOR LOCAL DEV ONLY.
 *
 * Mock shipping carrier that returns flat rates without contacting Correios. PAC is
 * always {@value #PAC_COST}, SEDEX always {@value #SEDEX_COST}, so quotes are
 * deterministic. The address is never inspected or validated here — the real
 * Correios adapter will quote per destination CEP and validate it.
 */
@ApplicationScoped
public class ShippingRateMockAdapter implements ShippingRatePort {

    public static final String PAC_COST = "15.00";
    public static final String SEDEX_COST = "30.00";
    public static final String CARRIER = "Correios";

    private static final Logger LOGGER = Logger.getLogger(ShippingRateMockAdapter.class.getName());

    public static final ShippingOption PAC =
            new ShippingOption("pac", new Money(new BigDecimal(PAC_COST)), 15, "PAC - standard ground");
    public static final ShippingOption SEDEX =
            new ShippingOption("sedex", new Money(new BigDecimal(SEDEX_COST)), 2, "SEDEX - express");

    @Override
    public List<ShippingOption> getQuotes(ShippingAddress address) {
        LOGGER.info("Mock shipping quote requested for postal code " + address.getPostalCode());
        return List.of(PAC, SEDEX);
    }

    @Override
    public ShippingLabel createLabel(Order order, ShippingOption selected) {
        String method = selected == null ? null : selected.method();
        if (selected == null || !(method.equals(PAC.method()) || method.equals(SEDEX.method()))) {
            throw new ShippingException("Shipping method not available: "
                    + (selected == null ? "null" : selected.method()));
        }
        String trackingNumber = "AA" + String.format("%09d",
                ThreadLocalRandom.current().nextInt(1_000_000_000)) + "BR";
        return new ShippingLabel(trackingNumber, CARRIER, method, Instant.now());
    }
}
