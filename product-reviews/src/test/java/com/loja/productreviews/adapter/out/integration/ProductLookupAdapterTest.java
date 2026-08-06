package com.loja.productreviews.adapter.out.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.productreviews.domain.port.out.ProductLookupPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class ProductLookupAdapterTest {

    private ProductRepositoryPort productRepository;
    private ProductLookupPort adapter;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepositoryPort.class);
        adapter = new ProductLookupAdapter(productRepository);
    }

    @Test
    void existsById_productFound_returnsTrue() {
        when(productRepository.findById("p-1")).thenReturn(Optional.of(mock(Product.class)));

        assertThat(adapter.existsById("p-1")).isTrue();
        verify(productRepository).findById("p-1");
    }

    @Test
    void existsById_productMissing_returnsFalse() {
        when(productRepository.findById("p-1")).thenReturn(Optional.empty());

        assertThat(adapter.existsById("p-1")).isFalse();
    }
}
