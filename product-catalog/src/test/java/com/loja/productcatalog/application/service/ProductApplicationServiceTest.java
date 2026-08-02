package com.loja.productcatalog.application.service;

import com.loja.productcatalog.application.dto.CreateProductCommand;
import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
import com.loja.productcatalog.application.dto.ProductSortField;
import com.loja.productcatalog.application.dto.SortDirection;
import com.loja.productcatalog.application.dto.UpdateProductCommand;
import com.loja.productcatalog.application.dto.UploadProductImageCommand;
import com.loja.productcatalog.domain.exception.DuplicateSkuException;
import com.loja.productcatalog.domain.exception.InvalidProductImageException;
import com.loja.productcatalog.domain.exception.ProductNotFoundException;
import com.loja.productcatalog.domain.exception.ProductValidationException;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductApplicationServiceTest {

    private final ProductRepositoryPort productRepository = mock(ProductRepositoryPort.class);
    private final CategoryRepositoryPort categoryRepository = mock(CategoryRepositoryPort.class);
    private final ProductImageStoragePort imageStorage = mock(ProductImageStoragePort.class);

    private ProductApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProductApplicationService(productRepository, categoryRepository, imageStorage);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.existsBySlug(any())).thenReturn(false);
        when(productRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(categoryRepository.existsById(any())).thenReturn(true);
        when(imageStorage.upload(any(byte[].class), anyString(), anyString()))
                .thenReturn("products/ABC-123/img.webp");
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_withDuplicateSku_throwsDuplicateSkuException() {
        when(productRepository.existsBySku(new Sku("ABC-123"))).thenReturn(true);

        assertThatThrownBy(() -> service.create(createCommand("ABC-123", null)))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_withBlankSlug_derivesSlugFromName() {
        Product saved = service.create(createCommand("ABC-123", null));

        assertThat(saved.getSlugValue()).isEqualTo("smartphone-x-2026");
        assertThat(saved.getSkuValue()).isEqualTo("ABC-123");
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    void create_normalizesSkuToUppercase() {
        Product saved = service.create(createCommand("  abc-123 ", null));

        assertThat(saved.getSkuValue()).isEqualTo("ABC-123");
    }

    @Test
    void create_withTakenSlug_appendsNumericSuffix() {
        when(productRepository.existsBySlug(new Slug("smartphone-x-2026"))).thenReturn(true);

        Product saved = service.create(createCommand("ABC-123", null));

        assertThat(saved.getSlugValue()).isEqualTo("smartphone-x-2026-2");
    }

    @Test
    void create_withProvidedSlug_usesIt() {
        Product saved = service.create(createCommand("ABC-123", "my-custom-slug"));

        assertThat(saved.getSlugValue()).isEqualTo("my-custom-slug");
    }

    @Test
    void create_withAccentedName_foldsAccentsInSlug() {
        Product saved = service.create(createCommand("ABC-123", null,
                "Café Especial"));

        assertThat(saved.getSlugValue()).isEqualTo("cafe-especial");
    }

    @Test
    void create_withHtmlDescription_sanitizesIt() {
        Product saved = service.create(createCommand("ABC-123", null,
                "Smartphone", "<script>alert(1)</script><p>Hello <b>world</b></p>"));

        assertThat(saved.getDescription()).contains("<p>Hello <b>world</b></p>");
        assertThat(saved.getDescription()).doesNotContain("<script");
    }

    @Test
    void create_savesProductAndReturnsIt() {
        Product saved = service.create(createCommand("ABC-123", null));

        verify(productRepository).save(saved);
        assertThat(saved.getPrice().getAmount()).isEqualByComparingTo("1000.00");
        assertThat(saved.getCategoryIds()).containsExactly(1L);
    }

    // ------------------------------------------------------------------ update

    @Test
    void update_unknownProduct_throwsProductNotFoundException() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", updateCommand("New name")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void update_withChangedSlugCollidingWithOtherProduct_throwsProductValidationException() {
        Product product = product();
        Product other = product("p2");
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));
        when(productRepository.findBySlug(new Slug("taken"))).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update("p1", updateCommand("Smartphone", "taken")))
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("Slug already in use");
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_withSameSlug_keepsSlugAndAppliesFields() {
        Product product = product();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.update("p1", updateCommand("Smartphone Pro"));

        assertThat(saved.getSlugValue()).isEqualTo("abc-123");
        assertThat(saved.getName()).isEqualTo("Smartphone Pro");
        verify(productRepository).save(product);
    }

    @Test
    void update_withHtmlDescription_sanitizesIt() {
        Product product = product();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.update("p1", updateCommand("Smartphone", null,
                "<script>x</script><i>italic</i>"));

        assertThat(saved.getDescription()).contains("<i>italic</i>");
        assertThat(saved.getDescription()).doesNotContain("<script");
    }

    @Test
    void update_replacesCategories() {
        Product product = product();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));
        UpdateProductCommand command = new UpdateProductCommand("Smartphone", null, null,
                null, 7, null, null, null, Set.of(2L, 3L));

        Product saved = service.update("p1", command);

        assertThat(saved.getCategoryIds()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(saved.getStock()).isEqualTo(7);
    }

    // ------------------------------------------------------------------ publish

    @Test
    void publish_unknownProduct_throwsProductNotFoundException() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish("missing"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void publish_withoutImageOrCategory_staysDraftAndThrows() {
        Product product = product();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.publish("p1"))
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("at least one image is required");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
        verify(productRepository, never()).save(any());
    }

    @Test
    void publish_withImageMissingAltText_throws() {
        Product product = product(image(1L, 0, true));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.publish("p1"))
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("alt text");
        verify(productRepository, never()).save(any());
    }

    @Test
    void publish_withDeletedCategory_throwsAndDoesNotSave() {
        Product product = product(image(1L, 0, true));
        product.getImages().forEach(i -> i.setAltText("A smartphone"));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));
        when(categoryRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.publish("p1"))
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("Category does not exist");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
        verify(productRepository, never()).save(any());
    }

    @Test
    void publish_validProduct_becomesActiveAndSaves() {
        Product product = product(image(1L, 0, true));
        product.getImages().forEach(i -> i.setAltText("A smartphone"));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.publish("p1");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        verify(productRepository).save(product);
    }

    @Test
    void publish_alreadyActiveProduct_throws() {
        Product product = product(ProductStatus.ACTIVE);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.publish("p1"))
                .isInstanceOf(ProductValidationException.class);
        verify(productRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ archive

    @Test
    void archive_unknownProduct_throwsProductNotFoundException() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive("missing"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void archive_validProduct_becomesArchivedAndSaves() {
        Product product = product();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.archive("p1");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        verify(productRepository).save(product);
    }

    @Test
    void archive_archivedProduct_throws() {
        Product product = product(ProductStatus.ARCHIVED);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.archive("p1"))
                .isInstanceOf(ProductValidationException.class);
        verify(productRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ search

    @Test
    void search_delegatesToRepository() {
        PageResult<Product> expected = new PageResult<>(List.of(product()), 1L, 0, 20);
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, null, null, null,
                null, 0, 20, ProductSortField.NAME, SortDirection.ASC);
        when(productRepository.search(criteria)).thenReturn(expected);

        assertThat(service.search(criteria)).isSameAs(expected);
    }

    @Test
    void findByName_delegatesToRepository() {
        when(productRepository.findByName("phone")).thenReturn(List.of(product()));

        assertThat(service.findByName("phone")).hasSize(1);
        verify(productRepository).findByName("phone");
    }

    @Test
    void findAll_delegatesToRepository() {
        when(productRepository.findAll()).thenReturn(List.of(product()));

        assertThat(service.findAll()).hasSize(1);
        verify(productRepository).findAll();
    }

    // ------------------------------------------------------------------ uploadImage

    @Test
    void uploadImage_unknownProduct_throwsProductNotFoundException() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadImage("missing", uploadCommand("image/jpeg", false)))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void uploadImage_withDisallowedContentType_throwsInvalidProductImageException() {
        Product product = product();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.uploadImage("p1", uploadCommand("image/gif", false)))
                .isInstanceOf(InvalidProductImageException.class);
        verify(imageStorage, never()).upload(any(), any(), any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void uploadImage_withFileOver5Mb_throwsInvalidProductImageException() {
        Product product = product();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));
        UploadProductImageCommand command = new UploadProductImageCommand(
                new byte[6 * 1024 * 1024], "image/jpeg", "alt", 0, false);

        assertThatThrownBy(() -> service.uploadImage("p1", command))
                .isInstanceOf(InvalidProductImageException.class);
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void uploadImage_withEightImagesAlready_throwsInvalidProductImageException() {
        Product product = product(image(1L, 0, false), image(2L, 1, false), image(3L, 2, false),
                image(4L, 3, false), image(5L, 4, false), image(6L, 5, false),
                image(7L, 6, false), image(8L, 7, false));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.uploadImage("p1", uploadCommand("image/jpeg", false)))
                .isInstanceOf(InvalidProductImageException.class)
                .hasMessageContaining("8 images");
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void uploadImage_firstImageBecomesPrimaryEvenWhenNotRequested() {
        Product product = product();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.uploadImage("p1", uploadCommand("image/jpeg", false));

        assertThat(saved.getImages()).hasSize(1);
        assertThat(saved.getImages().get(0).isPrimary()).isTrue();
        verify(imageStorage).upload(any(byte[].class), eq("image/jpeg"), eq("products/ABC-123"));
    }

    @Test
    void uploadImage_explicitPrimaryUnmarksPreviousPrimary() {
        Product product = product(image(1L, 0, true));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.uploadImage("p1", uploadCommand("image/png", true));

        assertThat(saved.getImages()).hasSize(2);
        assertThat(saved.getImages()).filteredOn(ProductImage::isPrimary).hasSize(1);
        ProductImage added = saved.getImages().stream()
                .filter(i -> i.getId() == null).findFirst().orElseThrow();
        assertThat(added.isPrimary()).isTrue();
    }

    @Test
    void uploadImage_uploadsToStorageAndSaves() {
        Product product = product();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.uploadImage("p1", uploadCommand("image/webp", false));

        verify(imageStorage).upload(any(byte[].class), eq("image/webp"), eq("products/ABC-123"));
        assertThat(saved.getImages()).extracting(ProductImage::getObjectKey)
                .containsExactly("products/ABC-123/img.webp");
        verify(productRepository).save(product);
    }

    // ------------------------------------------------------------------ updateImageMeta

    @Test
    void updateImageMeta_unknownProduct_throwsProductNotFoundException() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateImageMeta("missing", null, Map.of()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateImageMeta_withPrimaryImageId_marksItPrimary() {
        Product product = product(image(1L, 0, false), image(2L, 1, false));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.updateImageMeta("p1", 2L, Map.of());

        assertThat(saved.getImages()).filteredOn(ProductImage::isPrimary).hasSize(1);
        assertThat(saved.getImages().stream().filter(ProductImage::isPrimary).findFirst().orElseThrow()
                .getId()).isEqualTo(2L);
        verify(productRepository).save(product);
    }

    @Test
    void updateImageMeta_withUnknownPrimaryImage_throwsProductValidationException() {
        Product product = product(image(1L, 0, true));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.updateImageMeta("p1", 99L, Map.of()))
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("Image not found");
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateImageMeta_updatesAltTextOfMatchingImages() {
        Product product = product(image(1L, 0, true), image(2L, 1, false));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.updateImageMeta("p1", null, Map.of(1L, "Front view", 2L, "Back view"));

        assertThat(saved.getImages())
                .filteredOn(i -> i.getId().equals(1L))
                .extracting(ProductImage::getAltText)
                .containsExactly("Front view");
        assertThat(saved.getImages())
                .filteredOn(i -> i.getId().equals(2L))
                .extracting(ProductImage::getAltText)
                .containsExactly("Back view");
    }

    // ------------------------------------------------------------------ moveImage

    @Test
    void moveImage_unknownProduct_throwsProductNotFoundException() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveImage("missing", 1L, 1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void moveImage_withUnknownImage_throwsProductValidationException() {
        Product product = product(image(1L, 0, true));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.moveImage("p1", 99L, 1))
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("Image not found");
        verify(productRepository, never()).save(any());
    }

    @Test
    void moveImage_withOutOfBoundsPosition_throwsProductValidationException() {
        Product product = product(image(1L, 0, true), image(2L, 1, false));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.moveImage("p1", 1L, 5))
                .isInstanceOf(ProductValidationException.class)
                .hasMessageContaining("Invalid image position");
        verify(productRepository, never()).save(any());
    }

    @Test
    void moveImage_whenMovedForward_shiftsIntermediateImagesBack() {
        Product product = product(image(1L, 0, true), image(2L, 1, false), image(3L, 2, false));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.moveImage("p1", 1L, 2);

        assertThat(positionsById(saved)).containsEntry(1L, 2);
        assertThat(positionsById(saved)).containsEntry(2L, 0);
        assertThat(positionsById(saved)).containsEntry(3L, 1);
    }

    @Test
    void moveImage_whenMovedBackward_shiftsIntermediateImagesForward() {
        Product product = product(image(1L, 0, true), image(2L, 1, false), image(3L, 2, false));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        Product saved = service.moveImage("p1", 3L, 0);

        assertThat(positionsById(saved)).containsEntry(3L, 0);
        assertThat(positionsById(saved)).containsEntry(1L, 1);
        assertThat(positionsById(saved)).containsEntry(2L, 2);
    }

    private static Map<Long, Integer> positionsById(Product saved) {
        Map<Long, Integer> positions = new HashMap<>();
        saved.getImages().forEach(i -> positions.put(i.getId(), i.getPosition()));
        return positions;
    }

    // ------------------------------------------------------------------ fixtures

    private static CreateProductCommand createCommand(String sku, String slug) {
        return createCommand(sku, slug, "Smartphone X 2026", null);
    }

    private static CreateProductCommand createCommand(String sku, String slug, String name) {
        return createCommand(sku, slug, name, null);
    }

    private static CreateProductCommand createCommand(String sku, String slug, String name,
                                                      String description) {
        return new CreateProductCommand(sku, name, slug, "Short", description,
                money("1000.00"), null, 5, null, null, null, Set.of(1L));
    }

    private static UpdateProductCommand updateCommand(String name) {
        return updateCommand(name, null, null);
    }

    private static UpdateProductCommand updateCommand(String name, String slug) {
        return updateCommand(name, slug, null);
    }

    private static UpdateProductCommand updateCommand(String name, String slug, String description) {
        return new UpdateProductCommand(name, slug, "Short", description,
                5, null, null, null, Set.of(1L));
    }

    private static UploadProductImageCommand uploadCommand(String contentType, boolean primary) {
        return new UploadProductImageCommand(new byte[1024], contentType, "alt", 0, primary);
    }

    private static Product product() {
        return product(ProductStatus.DRAFT);
    }

    private static Product product(ProductStatus status) {
        return product("p1", status);
    }

    private static Product product(String id) {
        return product(id, ProductStatus.DRAFT);
    }

    private static Product product(String id, ProductStatus status) {
        return new Product(id, new Sku("ABC-123"), new Slug("abc-123"), "Smartphone",
                null, null, money("1000.00"), null, 5, status, null, null, null,
                new HashSet<>(Set.of(1L)), new ArrayList<>(List.of()));
    }

    private static Product product(ProductImage... images) {
        return new Product("p1", new Sku("ABC-123"), new Slug("abc-123"), "Smartphone",
                null, null, money("1000.00"), null, 5, ProductStatus.DRAFT, null, null, null,
                new HashSet<>(Set.of(1L)), new ArrayList<>(List.of(images)));
    }

    private static ProductImage image(Long id, int position, boolean primary) {
        return new ProductImage(id, "products/ABC-123/img.webp", null, position, primary);
    }

    private static Money money(String amount) {
        return new Money(new BigDecimal(amount));
    }
}
