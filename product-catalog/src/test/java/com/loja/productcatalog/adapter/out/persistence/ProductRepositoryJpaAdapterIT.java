package com.loja.productcatalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.application.dto.ProductSortField;
import com.loja.productcatalog.application.dto.SortDirection;
import com.loja.productcatalog.domain.exception.DuplicateSkuException;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.shared.domain.Money;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

class ProductRepositoryJpaAdapterIT extends AbstractIntegrationTest {

    private ProductRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new ProductRepositoryAdapter();
        adapter.em = em;
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE tb_product_image, tb_product_category, tb_product RESTART IDENTITY CASCADE")
                .executeUpdate();
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    /**
     * Runs a read against the repository inside an explicit transaction, mirroring the
     * container-managed transaction the adapter sees in production. Without a real
     * transaction, Hibernate's pool (autoCommit=false) leaves the JDBC connection in an
     * open implicit transaction that holds table locks and blocks the next setUp()'s TRUNCATE.
     */
    private <T> T inTx(Supplier<T> operation) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            T result = operation.get();
            tx.commit();
            return result;
        } catch (RuntimeException | Error e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.clear();
        }
    }

    @Test
    void shouldPersistAndReturnProductWithId() {
        Product product = product("p-save-1", "ABC-001", "produto-abc-001", "Produto ABC");

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        Product saved = adapter.save(product);
        tx.commit();
        em.clear();

        assertThat(saved.getId()).isEqualTo("p-save-1");
        assertThat(saved.getImages().get(0).getId()).isNotNull();
    }

    @Test
    void shouldRoundTripAllFieldsThroughFindById() {
        Product product = new Product(
                "p-rt-1", new Sku("ABC-002"), new Slug("produto-abc-002"),
                "Produto ABC 2", "Desc curta", "<p>Desc longa</p>",
                new Money(new BigDecimal("59.90")), new Money(new BigDecimal("80.00")),
                15, ProductStatus.INACTIVE, 250, "Meta title", "Meta description",
                Set.of(5L),
                List.of(new ProductImage(null, "products/ABC-002/foto.webp", "Foto do produto", 0, true)));

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(product);
        tx.commit();
        em.clear();

        Optional<Product> found = inTx(() -> adapter.findById(product.getId()));
        assertThat(found).isPresent();
        Product restored = found.get();
        assertThat(restored.getId()).isEqualTo("p-rt-1");
        assertThat(restored.getSku()).isEqualTo(new Sku("ABC-002"));
        assertThat(restored.getSlug()).isEqualTo(new Slug("produto-abc-002"));
        assertThat(restored.getName()).isEqualTo("Produto ABC 2");
        assertThat(restored.getShortDescription()).isEqualTo("Desc curta");
        assertThat(restored.getDescription()).isEqualTo("<p>Desc longa</p>");
        assertThat(restored.getPrice()).isEqualTo(new Money(new BigDecimal("59.90")));
        assertThat(restored.getCompareAtPrice()).isEqualTo(new Money(new BigDecimal("80.00")));
        assertThat(restored.getStock()).isEqualTo(15);
        assertThat(restored.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(restored.getWeightGrams()).isEqualTo(250);
        assertThat(restored.getMetaTitle()).isEqualTo("Meta title");
        assertThat(restored.getMetaDescription()).isEqualTo("Meta description");
        assertThat(restored.getCategoryIds()).containsExactly(5L);
        assertThat(restored.getImages()).hasSize(1);
        assertThat(restored.getImages().get(0).getId()).isNotNull();
        assertThat(restored.getImages().get(0).getObjectKey()).isEqualTo("products/ABC-002/foto.webp");
        assertThat(restored.getImages().get(0).getAltText()).isEqualTo("Foto do produto");
        assertThat(restored.getImages().get(0).isPrimary()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        assertThat(inTx(() -> adapter.findById("nonexistent-id"))).isEmpty();
    }

    @Test
    void shouldUpdateProductInPlace() {
        Product product = product("p-upd-1", "ABC-003", "produto-abc-003", "Antes");
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        Product saved = adapter.save(product);
        tx.commit();
        em.clear();

        saved.setName("Depois");

        tx.begin();
        adapter.save(saved);
        tx.commit();
        em.clear();

        Optional<Product> found = inTx(() -> adapter.findById(product.getId()));
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Depois");
        assertThat(found.get().getStock()).isEqualTo(15);
    }

    @Test
    void shouldRejectDuplicateSku() {
        Product first = product("p-dupe-1", "ABC-004", "produto-abc-004", "Primeiro");
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(first);
        tx.commit();
        em.clear();

        Product duplicate = product("p-dupe-2", "ABC-004", "produto-abc-004-b", "Segundo");

        tx.begin();
        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(DuplicateSkuException.class);
        tx.rollback();
        em.clear();
    }

    @Test
    void shouldPaginateActiveProducts() {
        saveAll(manyProducts(25));

        PageResult<Product> first = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, null, 0, 20, false, null, null)));
        assertThat(first.items()).hasSize(20);
        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(2);

        PageResult<Product> second = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, null, 1, 20, false, null, null)));
        assertThat(second.items()).hasSize(5);
    }

    @Test
    void shouldExcludeArchivedByDefault() {
        save(product("p-a-1", "SKU-A1", "produto-a-1", "Ativo", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-a-2", "SKU-A2", "produto-a-2", "Inativo", "20.00", ProductStatus.INACTIVE, Set.of(5L)));
        save(product("p-a-3", "SKU-A3", "produto-a-3", "Arquivado", "30.00", ProductStatus.ARCHIVED, Set.of(5L)));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, null, 0, 20, false, null, null)));
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(Product::getStatus).doesNotContain(ProductStatus.ARCHIVED);
    }

    @Test
    void shouldFilterByCategory() {
        save(product("p-c-1", "SKU-C1", "produto-c-1", "Categoria 5", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-c-2", "SKU-C2", "produto-c-2", "Categoria 6", "20.00", ProductStatus.ACTIVE, Set.of(6L)));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(null, 6L, null, null, null, 0, 20, false, null, null)));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getName()).isEqualTo("Categoria 6");
    }

    @Test
    void shouldFilterByNameOrSkuContainsCaseInsensitive() {
        save(product("p-t-1", "SKU-TENIS", "produto-t-1", "Tenis de Corrida", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-t-2", "SKU-CAM", "produto-t-2", "Camiseta", "20.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> byName = inTx(() -> adapter.search(new ProductSearchCriteria("tenis", null, null, null, null, 0, 20, false, null, null)));
        assertThat(byName.items()).hasSize(1);
        assertThat(byName.items().get(0).getName()).isEqualTo("Tenis de Corrida");

        PageResult<Product> bySku = inTx(() -> adapter.search(new ProductSearchCriteria("sku-cam", null, null, null, null, 0, 20, false, null, null)));
        assertThat(bySku.items()).hasSize(1);
        assertThat(bySku.items().get(0).getSkuValue()).isEqualTo("SKU-CAM");
    }

    @Test
    void shouldFilterByPriceRange() {
        save(product("p-pr-1", "SKU-PR1", "produto-pr-1", "Barato", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-pr-2", "SKU-PR2", "produto-pr-2", "Meio", "20.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-pr-3", "SKU-PR3", "produto-pr-3", "Caro", "30.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, new BigDecimal("15.00"), new BigDecimal("25.00"), null, 0, 20, false, null, null)));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getName()).isEqualTo("Meio");
    }

    @Test
    void shouldSortByPrice() {
        save(product("p-so-1", "SKU-SO1", "produto-so-1", "Caro", "30.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-so-2", "SKU-SO2", "produto-so-2", "Barato", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-so-3", "SKU-SO3", "produto-so-3", "Meio", "20.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> asc = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, null, 0, 20, false, ProductSortField.PRICE, SortDirection.ASC)));
        assertThat(asc.items()).extracting(Product::getName).containsExactly("Barato", "Meio", "Caro");

        PageResult<Product> desc = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, null, 0, 20, false, ProductSortField.PRICE, SortDirection.DESC)));
        assertThat(desc.items()).extracting(Product::getName).containsExactly("Caro", "Meio", "Barato");
    }

    @Test
    void shouldSortByName() {
        save(product("p-sn-1", "SKU-SN1", "produto-sn-1", "Bravo", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-sn-2", "SKU-SN2", "produto-sn-2", "Alpha", "20.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-sn-3", "SKU-SN3", "produto-sn-3", "Charlie", "30.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, null, 0, 20, false, ProductSortField.NAME, SortDirection.ASC)));
        assertThat(page.items()).extracting(Product::getName).containsExactly("Alpha", "Bravo", "Charlie");
    }

    @Test
    void shouldSortByCreatedAt() throws Exception {
        save(product("p-sc-1", "SKU-SC1", "produto-sc-1", "Primeiro", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        Thread.sleep(5);
        save(product("p-sc-2", "SKU-SC2", "produto-sc-2", "Segundo", "20.00", ProductStatus.ACTIVE, Set.of(5L)));
        Thread.sleep(5);
        save(product("p-sc-3", "SKU-SC3", "produto-sc-3", "Terceiro", "30.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, null, 0, 20, false, ProductSortField.CREATED_AT, SortDirection.ASC)));
        assertThat(page.items()).extracting(Product::getName).containsExactly("Primeiro", "Segundo", "Terceiro");
    }

    @Test
    void shouldFilterByExplicitStatus() {
        save(product("p-st-1", "SKU-ST1", "produto-st-1", "Ativo", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-st-2", "SKU-ST2", "produto-st-2", "Inativo", "20.00", ProductStatus.INACTIVE, Set.of(5L)));
        save(product("p-st-3", "SKU-ST3", "produto-st-3", "Arquivado", "30.00", ProductStatus.ARCHIVED, Set.of(5L)));

        PageResult<Product> archived = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, ProductStatus.ARCHIVED, 0, 20, false, null, null)));
        assertThat(archived.items()).hasSize(1);
        assertThat(archived.items().get(0).getName()).isEqualTo("Arquivado");

        PageResult<Product> active = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, ProductStatus.ACTIVE, 0, 20, false, null, null)));
        assertThat(active.items()).extracting(Product::getName).containsExactly("Ativo");
    }

    @Test
    void shouldReturnEmptyWhenNoMatches() {
        save(product("p-nm-1", "SKU-NM1", "produto-nm-1", "Existente", "10.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria("nao existe", null, null, null, null, 0, 20, false, null, null)));
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    // -------------------------------------------------------------- FTS text search (V25)

    @Test
    void shouldRankRelevanceForTextSearch() {
        save(productDetailed("p-ft-1", "SKU-FT1", "produto-ft-1", "Smartphone X",
                "smartphone smartphone", null, "59.90", ProductStatus.ACTIVE));
        save(productDetailed("p-ft-2", "SKU-FT2", "produto-ft-2", "Phone Case",
                "case for smartphone", null, "19.90", ProductStatus.ACTIVE));
        save(productDetailed("p-ft-3", "SKU-FT3", "produto-ft-3", "Running Shoes",
                null, null, "89.90", ProductStatus.ACTIVE));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(
                "smartphone", null, null, null, null, 0, 20, false, null, null)));

        assertThat(page.items()).extracting(Product::getName)
                .containsExactly("Smartphone X", "Phone Case");
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void shouldMatchWordsIndependentlyOfPosition() {
        save(productDetailed("p-tk-1", "SKU-TK1", "produto-tk-1", "Audio Gear",
                "high quality audio headphones", null, "99.90", ProductStatus.ACTIVE));
        save(productDetailed("p-tk-2", "SKU-TK2", "produto-tk-2", "Headphones Deluxe",
                null, null, "129.90", ProductStatus.ACTIVE));

        // "audio headp" is not a contiguous LIKE substring, but both tokens match
        // the first product's full-text vector independently.
        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(
                "audio headp", null, null, null, null, 0, 20, false, null, null)));

        assertThat(page.items()).extracting(Product::getName)
                .containsExactly("Audio Gear");
    }

    @Test
    void shouldMatchByWordPrefix() {
        save(product("p-pf-1", "SKU-PF1", "produto-pf-1", "Smartphone", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-pf-2", "SKU-PF2", "produto-pf-2", "Smart Watch", "20.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(
                "smartp", null, null, null, null, 0, 20, false, null, null)));

        assertThat(page.items()).extracting(Product::getName).containsExactly("Smartphone");
    }

    @Test
    void shouldCombineTextSearchWithCategoryAndPriceFilters() {
        save(product("p-fc-1", "SKU-FC1", "produto-fc-1", "Tenis Pro", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-fc-2", "SKU-FC2", "produto-fc-2", "Tenis Luxo", "30.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-fc-3", "SKU-FC3", "produto-fc-3", "Tenis Barato", "5.00", ProductStatus.ACTIVE, Set.of(6L)));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(
                "tenis", 5L, new BigDecimal("8.00"), null, null, 0, 20, false, null, null)));

        assertThat(page.items()).extracting(Product::getName)
                .containsExactlyInAnyOrder("Tenis Pro", "Tenis Luxo");
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void shouldExcludeArchivedInTextSearchByDefault() {
        save(product("p-fta-1", "SKU-FTA1", "produto-fta-1", "Tenis Ativo", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-fta-2", "SKU-FTA2", "produto-fta-2", "Tenis Arquivado", "20.00", ProductStatus.ARCHIVED, Set.of(5L)));

        PageResult<Product> defaultFilter = inTx(() -> adapter.search(new ProductSearchCriteria(
                "tenis", null, null, null, null, 0, 20, false, null, null)));
        assertThat(defaultFilter.items()).extracting(Product::getName).containsExactly("Tenis Ativo");

        PageResult<Product> explicitArchived = inTx(() -> adapter.search(new ProductSearchCriteria(
                "tenis", null, null, null, ProductStatus.ARCHIVED, 0, 20, false, null, null)));
        assertThat(explicitArchived.items()).extracting(Product::getName).containsExactly("Tenis Arquivado");
    }

    @Test
    void shouldPaginateTextSearch() {
        save(product("p-pg-1", "SKU-PG1", "produto-pg-1", "Smartphone X1", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-pg-2", "SKU-PG2", "produto-pg-2", "Smartphone X2", "10.00", ProductStatus.ACTIVE, Set.of(5L)));
        save(product("p-pg-3", "SKU-PG3", "produto-pg-3", "Smartphone X3", "10.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> first = inTx(() -> adapter.search(new ProductSearchCriteria(
                "smartphone", null, null, null, null, 0, 2, false, null, null)));
        assertThat(first.items()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(3);

        PageResult<Product> second = inTx(() -> adapter.search(new ProductSearchCriteria(
                "smartphone", null, null, null, null, 1, 2, false, null, null)));
        assertThat(second.items()).hasSize(1);
        assertThat(second.totalElements()).isEqualTo(3);
    }

    @Test
    void shouldFallBackToLikePathWhenTermHasNoFtsTokens() {
        save(product("p-fb-1", "SKU-FB1", "produto-fb-1", "Telefone", "10.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> punctuation = inTx(() -> adapter.search(new ProductSearchCriteria(
                "!!!", null, null, null, null, 0, 20, false, null, null)));
        assertThat(punctuation.items()).isEmpty();
        assertThat(punctuation.totalElements()).isZero();

        PageResult<Product> stopwords = inTx(() -> adapter.search(new ProductSearchCriteria(
                "the and of", null, null, null, null, 0, 20, false, null, null)));
        assertThat(stopwords.items()).isEmpty();
        assertThat(stopwords.totalElements()).isZero();
    }

    @Test
    void shouldFallBackToLikePathForDigitOnlyTerms() {
        // "123" has no FTS lexeme, so the search falls back to the LIKE path and
        // still finds the SKU substring match.
        save(product("p-hy-1", "SKU-XYZ-123", "produto-hy-1", "Generic", "10.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> page = inTx(() -> adapter.search(new ProductSearchCriteria(
                "123", null, null, null, null, 0, 20, false, null, null)));

        assertThat(page.items()).extracting(Product::getName).containsExactly("Generic");
    }

    @Test
    void shouldClampPageSizeAndNormalizePage() {
        save(product("p-cl-1", "SKU-CL1", "produto-cl-1", "Clamp", "10.00", ProductStatus.ACTIVE, Set.of(5L)));

        PageResult<Product> defaults = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, null, -1, 0, false, null, null)));
        assertThat(defaults.page()).isZero();
        assertThat(defaults.pageSize()).isEqualTo(ProductSearchCriteria.DEFAULT_PAGE_SIZE);
        assertThat(defaults.items()).hasSize(1);

        PageResult<Product> capped = inTx(() -> adapter.search(new ProductSearchCriteria(null, null, null, null, null, 0, 1000, false, null, null)));
        assertThat(capped.pageSize()).isEqualTo(ProductSearchCriteria.MAX_PAGE_SIZE);
    }

    @Test
    void existsBySkuIsCaseInsensitive() {
        save(product("p-sk-1", "ABC-123", "produto-sk-1", "Sku Case"));

        assertThat(inTx(() -> adapter.existsBySku(new Sku("abc-123")))).isTrue();
        assertThat(inTx(() -> adapter.existsBySku(new Sku("ABC-123")))).isTrue();
        assertThat(inTx(() -> adapter.existsBySku(new Sku("ZZZ-999")))).isFalse();
    }

    @Test
    void existsBySlugMatches() {
        save(product("p-sl-1", "SKU-SL1", "produto-slug-1", "Slug Case"));

        assertThat(inTx(() -> adapter.existsBySlug(new Slug("produto-slug-1")))).isTrue();
        assertThat(inTx(() -> adapter.existsBySlug(new Slug("outro-slug")))).isFalse();
    }

    @Test
    void findBySkuAndBySlug() {
        save(product("p-f-1", "SKU-FIND", "produto-find-1", "Encontrado"));

        assertThat(inTx(() -> adapter.findBySku(new Sku("SKU-FIND")))).isPresent()
                .get().extracting(Product::getName).isEqualTo("Encontrado");
        assertThat(inTx(() -> adapter.findBySku(new Sku("SKU-AUSENTE")))).isEmpty();

        assertThat(inTx(() -> adapter.findBySlug(new Slug("produto-find-1")))).isPresent()
                .get().extracting(Product::getName).isEqualTo("Encontrado");
        assertThat(inTx(() -> adapter.findBySlug(new Slug("slug-ausente")))).isEmpty();
    }

    @Test
    void shouldDecrementStockAndReturnAffectedCount() {
        save(product("p-dec-1", "SKU-DEC1", "produto-dec-1", "Decremento", "59.90", ProductStatus.ACTIVE, Set.of(5L)));

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        int affected = adapter.decrementStock("p-dec-1", 2);
        tx.commit();
        em.clear();

        assertThat(affected).isEqualTo(1);
        assertThat(inTx(() -> adapter.findById("p-dec-1"))).isPresent()
                .get().extracting(Product::getStock).isEqualTo(13);
    }

    @Test
    void shouldReturnZeroAndNotModifyWhenStockInsufficient() {
        save(product("p-dec-2", "SKU-DEC2", "produto-dec-2", "Sem estoque", "59.90", ProductStatus.ACTIVE, Set.of(5L)));

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        int affected = adapter.decrementStock("p-dec-2", 999);
        tx.commit();
        em.clear();

        assertThat(affected).isZero();
        assertThat(inTx(() -> adapter.findById("p-dec-2"))).isPresent()
                .get().extracting(Product::getStock).isEqualTo(15);
    }

    @Test
    void shouldReturnZeroWhenProductDoesNotExist() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        int affected = adapter.decrementStock("p-inexistente", 1);
        tx.commit();

        assertThat(affected).isZero();
    }

    @Test
    void shouldDecrementAtomicallyUnderConcurrency() throws Exception {
        Product racingProduct = product("p-race-1", "SKU-RACE1", "produto-race-1", "Corrida", "59.90", ProductStatus.ACTIVE, Set.of(5L));
        racingProduct.setStock(1);
        save(racingProduct);
        em.clear();

        int poolSize = 2;
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch ready = new CountDownLatch(poolSize);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < poolSize; i++) {
            futures.add(executor.submit(() -> {
                EntityManager workerEm = emf.createEntityManager();
                ProductRepositoryAdapter workerAdapter = new ProductRepositoryAdapter();
                workerAdapter.em = workerEm;
                try {
                    ready.countDown();
                    go.await();
                    EntityTransaction workerTx = workerEm.getTransaction();
                    workerTx.begin();
                    int affected = workerAdapter.decrementStock("p-race-1", 1);
                    workerTx.commit();
                    return affected;
                } finally {
                    workerEm.close();
                }
            }));
        }

        ready.await();
        go.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(inTx(() -> adapter.findById("p-race-1"))).isPresent()
                .get().extracting(Product::getStock).isEqualTo(0);
    }

    private void save(Product product) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(product);
        tx.commit();
        em.clear();
    }

    private void saveAll(List<Product> products) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        for (Product product : products) {
            adapter.save(product);
        }
        tx.commit();
        em.clear();
    }

    private List<Product> manyProducts(int count) {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            products.add(product("p-bulk-" + i, "SKU-BULK-" + i, "produto-bulk-" + i, "Bulk " + i));
        }
        return products;
    }

    private Product product(String id, String sku, String slug, String name) {
        return product(id, sku, slug, name, "59.90", ProductStatus.DRAFT, Set.of(5L));
    }

    private Product product(String id, String sku, String slug, String name, String price, ProductStatus status, Set<Long> categories) {
        return new Product(
                id, new Sku(sku), new Slug(slug), name, null, null,
                new Money(new BigDecimal(price)), null, 15, status,
                null, null, null, categories,
                List.of(new ProductImage(null, "products/" + sku + "/foto.webp", "Foto do produto", 0, true)));
    }

    private Product productDetailed(String id, String sku, String slug, String name,
                                    String shortDescription, String description,
                                    String price, ProductStatus status) {
        return new Product(
                id, new Sku(sku), new Slug(slug), name, shortDescription, description,
                new Money(new BigDecimal(price)), null, 15, status,
                null, null, null, Set.of(5L),
                List.of(new ProductImage(null, "products/" + sku + "/foto.webp", "Foto do produto", 0, true)));
    }
}
