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
}
