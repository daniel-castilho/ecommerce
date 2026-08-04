package com.loja.productcatalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.shared.event.DomainEventPublisherPort;
import com.loja.shared.event.ProductArchivedEvent;

class ProductApplicationServiceArchiveTest {

    private ProductRepositoryPort repo;
    private CategoryRepositoryPort categoryRepo;
    private ProductImageStoragePort imageStorage;
    private DomainEventPublisherPort eventPublisher;
    private ProductApplicationService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProductRepositoryPort.class);
        categoryRepo = mock(CategoryRepositoryPort.class);
        imageStorage = mock(ProductImageStoragePort.class);
        eventPublisher = mock(DomainEventPublisherPort.class);
        service = new ProductApplicationService(repo, categoryRepo, imageStorage, eventPublisher);
    }

    @Test
    void archive_publishes_event_with_sku_and_name() {
        String id = "p-123";
        com.loja.shared.domain.Money price = new com.loja.shared.domain.Money(new java.math.BigDecimal("9.99"));
        com.loja.shared.domain.Money compareAt = new com.loja.shared.domain.Money(new java.math.BigDecimal("19.99"));
        Product product = new Product(id, new Sku("SKU1"), new Slug("slug"), "Name", "short", "desc", price, compareAt, 10, ProductStatus.ACTIVE, 100, "meta", "meta", java.util.Set.of(), java.util.List.of());
        when(repo.findById(id)).thenReturn(Optional.of(product));
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.archive(id);

        ArgumentCaptor<ProductArchivedEvent> captor = ArgumentCaptor.forClass(ProductArchivedEvent.class);
        verify(eventPublisher, times(1)).publish(captor.capture());
        ProductArchivedEvent evt = captor.getValue();
        assertThat(evt.productId()).isEqualTo(id);
        assertThat(evt.sku()).isEqualTo("SKU1");
        assertThat(evt.name()).isEqualTo("Name");
        assertThat(evt.occurredAt()).isBeforeOrEqualTo(Instant.now());
    }
}
