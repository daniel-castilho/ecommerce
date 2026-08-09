package com.loja.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void subtract_reducesAmount() {
        Money result = new Money(new BigDecimal("35.00")).subtract(new Money(new BigDecimal("7.00")));

        assertThat(result.getAmount()).isEqualByComparingTo("28.00");
    }

    @Test
    void subtract_equalAmounts_yieldsZero() {
        Money result = new Money(new BigDecimal("10.00")).subtract(new Money(new BigDecimal("10.00")));

        assertThat(result.getAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void subtract_roundsHalfEvenToTwoDecimals() {
        Money result = new Money(new BigDecimal("10.01")).subtract(new Money(new BigDecimal("0.005")));

        assertThat(result.getAmount()).isEqualByComparingTo("10.01");
    }

    @Test
    void multiply_bigDecimalFactor_roundsHalfEven() {
        Money result = new Money(new BigDecimal("36.50")).multiply(new BigDecimal("0.10"));

        assertThat(result.getAmount()).isEqualByComparingTo("3.65");
    }

    @Test
    void multiply_bigDecimalFactorRepeating_roundsHalfEven() {
        Money result = new Money(new BigDecimal("10.00")).multiply(new BigDecimal("0.3333"));

        assertThat(result.getAmount()).isEqualByComparingTo("3.33");
    }

    @Test
    void multiply_intFactor_matchesBigDecimalFactor() {
        Money byInt = new Money(new BigDecimal("3.50")).multiply(2);
        Money byBig = new Money(new BigDecimal("3.50")).multiply(new BigDecimal("2"));

        assertThat(byInt).isEqualTo(byBig);
        assertThat(byInt.getAmount()).isEqualByComparingTo("7.00");
    }
}
