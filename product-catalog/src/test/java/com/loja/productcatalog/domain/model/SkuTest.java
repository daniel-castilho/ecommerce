package com.loja.productcatalog.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkuTest {

    @Test
    void shouldNormalizeToUppercaseAndTrim() {
        assertThat(new Sku("  abc-123  ").getValue()).isEqualTo("ABC-123");
    }

    @Test
    void shouldAcceptAlreadyNormalizedValue() {
        assertThat(new Sku("ABC-123").getValue()).isEqualTo("ABC-123");
    }

    @Test
    void shouldAcceptSingleWord() {
        assertThat(new Sku("abc").getValue()).isEqualTo("ABC");
    }

    @Test
    void shouldRejectUnderscores() {
        assertThatThrownBy(() -> new Sku("abc_123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectSpacesInside() {
        assertThatThrownBy(() -> new Sku("abc 123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectLeadingHyphen() {
        assertThatThrownBy(() -> new Sku("-abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectTrailingHyphen() {
        assertThatThrownBy(() -> new Sku("abc-"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDoubleHyphen() {
        assertThatThrownBy(() -> new Sku("a--b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> new Sku(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectEmpty() {
        assertThatThrownBy(() -> new Sku("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectOverMaxLength() {
        assertThatThrownBy(() -> new Sku("A".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldImplementValueEquality() {
        Sku first = new Sku("abc-123");
        Sku second = new Sku("ABC-123");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
