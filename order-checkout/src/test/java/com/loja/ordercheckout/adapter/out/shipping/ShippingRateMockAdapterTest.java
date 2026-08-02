package com.loja.ordercheckout.adapter.out.shipping;

import com.loja.ordercheckout.domain.exception.ShippingException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.ShippingAddress;
import com.loja.ordercheckout.domain.model.ShippingLabel;
import com.loja.ordercheckout.domain.model.ShippingOption;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShippingRateMockAdapterTest {

    private static final Pattern CORREIOS_TRACKING = Pattern.compile("AA\\d{9}BR");

    private ShippingRateMockAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ShippingRateMockAdapter();
    }

    private ShippingAddress saoPauloAddress() {
        return new ShippingAddress("Ana Souza", "Rua das Flores", "123", null,
                "Centro", "Sao Paulo", "SP", "01310-100", null);
    }

    @Test
    void getQuotes_returnsPacAndSedex() {
        List<ShippingOption> options = adapter.getQuotes(saoPauloAddress());

        assertThat(options).hasSize(2);
        assertThat(options).extracting(ShippingOption::method).containsExactly("pac", "sedex");
        assertThat(options).extracting(ShippingOption::cost).containsExactly(
                new Money(new BigDecimal("15.00")), new Money(new BigDecimal("30.00")));
    }

    @Test
    void getQuotes_costIsConsistentAcrossCalls() {
        List<ShippingOption> first = adapter.getQuotes(saoPauloAddress());
        List<ShippingOption> second = adapter.getQuotes(saoPauloAddress());

        assertThat(second).isEqualTo(first);
        assertThat(second.get(0).cost()).isEqualTo(first.get(0).cost());
    }

    @Test
    void getQuotes_neverInspectsOrValidatesAddress() {
        ShippingAddress otherCity = new ShippingAddress("Joao", "Av Paulista", "10", null,
                "Bela Vista", "Curitiba", "PR", "80010-100", null);

        assertThat(adapter.getQuotes(otherCity)).containsExactlyInAnyOrder(
                adapter.PAC, adapter.SEDEX);
    }

    @Test
    void createLabel_returnsCorreiosTrackingNumber() {
        Order order = new Order("order-1", "user-1");
        ShippingLabel label = adapter.createLabel(order, adapter.PAC);

        assertThat(label.trackingNumber()).matches(CORREIOS_TRACKING);
        assertThat(label.carrier()).isEqualTo("Correios");
        assertThat(label.method()).isEqualTo("pac");
        assertThat(label.createdAt()).isNotNull();
    }

    @Test
    void createLabel_unknownMethodThrows() {
        Order order = new Order("order-1", "user-1");
        ShippingOption overnight = new ShippingOption("overnight",
                new Money(new BigDecimal("99.00")), 1, "not offered");

        assertThatThrownBy(() -> adapter.createLabel(order, overnight))
                .isInstanceOf(ShippingException.class)
                .hasMessageContaining("Shipping method not available");
        assertThatThrownBy(() -> adapter.createLabel(order, null))
                .isInstanceOf(ShippingException.class);
    }

    @Test
    void shippingOption_isImmutableAndEqualsByValue() {
        ShippingOption a = new ShippingOption("pac", new Money(new BigDecimal("15.00")), 15, "PAC");
        ShippingOption same = new ShippingOption("pac", new Money(new BigDecimal("15.00")), 15, "PAC");
        ShippingOption differentCost =
                new ShippingOption("pac", new Money(new BigDecimal("16.00")), 15, "PAC");

        assertThat(a).isEqualTo(same);
        assertThat(a).hasSameHashCodeAs(same);
        assertThat(a).isNotEqualTo(differentCost);
    }
}
