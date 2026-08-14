package com.loja.productcatalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductFtsQueryBuilderTest {

    @Test
    void buildPrefixTsQuery_singleWord_appendsPrefixOperator() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("smart"))
                .isEqualTo("smart:*");
    }

    @Test
    void buildPrefixTsQuery_multiWord_andsPrefixes() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("smart phone"))
                .isEqualTo("smart:* & phone:*");
    }

    @Test
    void buildPrefixTsQuery_isCaseInsensitive() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("Smart PHONE"))
                .isEqualTo("smart:* & phone:*");
    }

    @Test
    void buildPrefixTsQuery_stripsPunctuation() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("smart-phone!"))
                .isEqualTo("smart:* & phone:*");
    }

    @Test
    void buildPrefixTsQuery_dropsStopwords() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("the best phone"))
                .isEqualTo("best:* & phone:*");
    }

    @Test
    void buildPrefixTsQuery_dropsAllDigitTokens() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("2024 phone"))
                .isEqualTo("phone:*");
    }

    @Test
    void buildPrefixTsQuery_keepsAccentedLetters() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("camiseta"))
                .isEqualTo("camiseta:*");
    }

    @Test
    void buildPrefixTsQuery_onlyPunctuation_returnsEmpty() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("!!!"))
                .isEmpty();
    }

    @Test
    void buildPrefixTsQuery_onlyStopwords_returnsEmpty() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("the and of"))
                .isEmpty();
    }

    @Test
    void buildPrefixTsQuery_onlyDigits_returnsEmpty() {
        assertThat(ProductRepositoryAdapter.buildPrefixTsQuery("123 456"))
                .isEmpty();
    }

    @Test
    void sanitizeHeadline_keepsOnlyMarkTags() {
        assertThat(ProductRepositoryAdapter.sanitizeHeadline(
                "<mark>Smart</mark> phone <b>pro</b> <script>alert(1)</script>"))
                .isEqualTo("<mark>Smart</mark> phone &lt;b&gt;pro&lt;/b&gt; &lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void sanitizeHeadline_escapesAmpersands() {
        assertThat(ProductRepositoryAdapter.sanitizeHeadline("A & B <mark>cable</mark>"))
                .isEqualTo("A &amp; B <mark>cable</mark>");
    }

    @Test
    void sanitizeHeadline_plainText_passesThrough() {
        assertThat(ProductRepositoryAdapter.sanitizeHeadline("Just a very fine cable"))
                .isEqualTo("Just a very fine cable");
    }

    @Test
    void sanitizeHeadline_blankOrNull_returnsNull() {
        assertThat(ProductRepositoryAdapter.sanitizeHeadline(null)).isNull();
        assertThat(ProductRepositoryAdapter.sanitizeHeadline("  ")).isNull();
    }
}
