package com.loja.productcatalog.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugTest {

    @Test
    void shouldAcceptUrlSafeSlug() {
        assertThat(new Slug("my-product").getValue()).isEqualTo("my-product");
    }

    @Test
    void shouldRejectMixedCase() {
        assertThatThrownBy(() -> new Slug("My-Product"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectSpaces() {
        assertThatThrownBy(() -> new Slug("my product"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUnderscores() {
        assertThatThrownBy(() -> new Slug("my_product"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAccentedCharacters() {
        assertThatThrownBy(() -> new Slug("maçã"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> new Slug(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectEmpty() {
        assertThatThrownBy(() -> new Slug(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectLeadingHyphen() {
        assertThatThrownBy(() -> new Slug("-abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldImplementValueEquality() {
        Slug first = new Slug("my-product");
        Slug second = new Slug("my-product");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
