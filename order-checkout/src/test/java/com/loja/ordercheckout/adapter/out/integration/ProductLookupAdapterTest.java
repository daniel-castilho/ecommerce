package com.loja.ordercheckout.adapter.out.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loja.ordercheckout.application.dto.ProductSnapshot;
import com.loja.ordercheckout.domain.port.out.ProductLookupPort;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.shared.domain.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductLookupAdapterTest {

    private ProductRepositoryPort productRepository;
    private ProductImageStoragePort imageStorage;
    private ProductLookupPort adapter;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepositoryPort.class);
        imageStorage = mock(ProductImageStoragePort.class);
        adapter = new ProductLookupAdapter(productRepository, imageStorage);
    }

    @Test
    void findActiveById_activeProduct_returnsSnapshot() {
        Product product = product("p-1", ProductStatus.ACTIVE);
        when(productRepository.findById("p-1")).thenReturn(Optional.of(product));

        Optional<ProductSnapshot> result = adapter.findActiveById("p-1");

        assertThat(result).isPresent();
        ProductSnapshot snapshot = result.get();
        assertThat(snapshot.productId()).isEqualTo("p-1");
        assertThat(snapshot.name()).isEqualTo("Smartphone");
        assertThat(snapshot.slug()).isEqualTo("smartphone");
        assertThat(snapshot.price()).isEqualTo(new Money(new BigDecimal("999.90")));
        assertThat(snapshot.imageUrl()).isNull();
        verify(productRepository).findById("p-1");
    }

    @Test
    void findActiveById_activeProductWithPrimaryImage_resolvesPublicUrl() {
        ProductImage primary = new ProductImage(1L, "products/p-1/main.webp", null, 0, true);
        ProductImage secondary = new ProductImage(2L, "products/p-1/side.webp", null, 1, false);
        Product product = product("p-1", ProductStatus.ACTIVE, primary, secondary);
        when(productRepository.findById("p-1")).thenReturn(Optional.of(product));
        when(imageStorage.publicUrlFor("products/p-1/main.webp"))
                .thenReturn("https://cdn.example/main.webp");

        Optional<ProductSnapshot> result = adapter.findActiveById("p-1");

        assertThat(result).isPresent();
        assertThat(result.get().imageUrl()).isEqualTo("https://cdn.example/main.webp");
    }

    @Test
    void findActiveById_noPrimaryImage_usesLowestPosition() {
        ProductImage first = new ProductImage(1L, "products/p-1/a.webp", null, 2, false);
        ProductImage second = new ProductImage(2L, "products/p-1/b.webp", null, 0, false);
        Product product = product("p-1", ProductStatus.ACTIVE, first, second);
        when(productRepository.findById("p-1")).thenReturn(Optional.of(product));
        when(imageStorage.publicUrlFor("products/p-1/b.webp"))
                .thenReturn("https://cdn.example/b.webp");

        Optional<ProductSnapshot> result = adapter.findActiveById("p-1");

        assertThat(result).isPresent();
        assertThat(result.get().imageUrl()).isEqualTo("https://cdn.example/b.webp");
    }

    @Test
    void findActiveById_draftProduct_returnsEmpty() {
        when(productRepository.findById("p-1"))
                .thenReturn(Optional.of(product("p-1", ProductStatus.DRAFT)));

        assertThat(adapter.findActiveById("p-1")).isEmpty();
    }

    @Test
    void findActiveById_inactiveProduct_returnsEmpty() {
        when(productRepository.findById("p-1"))
                .thenReturn(Optional.of(product("p-1", ProductStatus.INACTIVE)));

        assertThat(adapter.findActiveById("p-1")).isEmpty();
    }

    @Test
    void findActiveById_missingProduct_returnsEmpty() {
        when(productRepository.findById("p-missing")).thenReturn(Optional.empty());

        assertThat(adapter.findActiveById("p-missing")).isEmpty();
    }

    private static Product product(String id, ProductStatus status, ProductImage... images) {
        return new Product(
                id,
                new Sku("ABC-123"),
                new Slug("smartphone"),
                "Smartphone",
                null,
                null,
                new Money(new BigDecimal("999.90")),
                null,
                5,
                status,
                null,
                null,
                null,
                new HashSet<>(Set.of(1L)),
                new ArrayList<>(List.of(images)));
    }
}
