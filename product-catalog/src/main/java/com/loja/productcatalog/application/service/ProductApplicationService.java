package com.loja.productcatalog.application.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.loja.productcatalog.application.dto.CreateProductCommand;
import com.loja.productcatalog.application.dto.PageResult;
import com.loja.productcatalog.application.dto.ProductSearchCriteria;
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
import com.loja.productcatalog.domain.port.in.ActivateProductUseCase;
import com.loja.productcatalog.domain.port.in.ArchiveProductUseCase;
import com.loja.productcatalog.domain.port.in.CreateProductUseCase;
import com.loja.productcatalog.domain.port.in.FindProductByIdUseCase;
import com.loja.productcatalog.domain.port.in.GetProductDetailUseCase;
import com.loja.productcatalog.domain.port.in.PublishProductUseCase;
import com.loja.productcatalog.domain.port.in.SearchProductsUseCase;
import com.loja.productcatalog.domain.port.in.UpdateProductImageUseCase;
import com.loja.productcatalog.domain.port.in.UpdateProductUseCase;
import com.loja.productcatalog.domain.port.in.UploadProductImageUseCase;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import com.loja.productcatalog.domain.port.out.ProductRepositoryPort;
import com.loja.shared.event.DomainEventPublisherPort;
import com.loja.shared.event.ProductArchivedEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Application service implementing all product-catalog use cases (spec §5, plus image
 * management added in spec §8 for the admin UI).
 * Coordinates the repository, the category repository and the image storage port;
 * business rules that only need the product's own state live on the domain object,
 * rules that must consult a port live here.
 */
@ApplicationScoped
@Transactional
public class ProductApplicationService
        implements CreateProductUseCase, UpdateProductUseCase, PublishProductUseCase,
                   ArchiveProductUseCase, ActivateProductUseCase, SearchProductsUseCase, GetProductDetailUseCase,
                   UploadProductImageUseCase, UpdateProductImageUseCase, FindProductByIdUseCase {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_IMAGES_PER_PRODUCT = 8;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final ProductRepositoryPort productRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final ProductImageStoragePort imageStorage;
    private final DomainEventPublisherPort eventPublisher;

    @Inject
    public ProductApplicationService(ProductRepositoryPort productRepository,
                                     CategoryRepositoryPort categoryRepository,
                                     ProductImageStoragePort imageStorage,
                                     DomainEventPublisherPort eventPublisher) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.imageStorage = imageStorage;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Product create(CreateProductCommand command) {
        Sku sku = new Sku(command.sku());
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("Product SKU already exists: " + sku.getValue());
        }

        Slug slug = uniqueSlug(command.slug() != null && !command.slug().isBlank()
                ? command.slug()
                : slugify(command.name()));

        Product product = new Product(UUID.randomUUID().toString(), sku, slug, command.name(),
                command.shortDescription(), sanitizeDescription(command.description()),
                command.price(), command.compareAtPrice(), command.costPrice(), command.stock(),
                ProductStatus.DRAFT, command.weightGrams(), command.metaTitle(),
                command.metaDescription(), command.categoryIds(), List.of());
        return productRepository.save(product);
    }

    @Override
    public Product update(String productId, UpdateProductCommand command) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        if (command.slug() != null && !command.slug().isBlank()
                && !command.slug().equals(product.getSlugValue())) {
            Slug newSlug = new Slug(command.slug());
            boolean collides = productRepository.findBySlug(newSlug)
                    .map(owner -> !owner.getId().equals(productId))
                    .orElse(false);
            if (collides) {
                throw new ProductValidationException("Slug already in use: " + command.slug());
            }
            product.changeSlug(newSlug);
        }

        product.setName(command.name());
        product.setShortDescription(command.shortDescription());
        product.setDescription(sanitizeDescription(command.description()));
        product.setCostPrice(command.costPrice());
        product.setStock(command.stock());
        product.setWeightGrams(command.weightGrams());
        product.setMetaTitle(command.metaTitle());
        product.setMetaDescription(command.metaDescription());
        product.replaceCategories(command.categoryIds() != null ? command.categoryIds() : Set.of());
        return productRepository.save(product);
    }

    @Override
    public Product publish(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        if (!product.canTransitionTo(ProductStatus.ACTIVE)) {
            throw new ProductValidationException(
                    "Product cannot be published from status " + product.getStatus());
        }
        product.validateForPublishing();

        for (Long categoryId : product.getCategoryIds()) {
            if (!categoryRepository.existsById(categoryId)) {
                throw new ProductValidationException(
                        "Category does not exist: " + categoryId);
            }
        }

        product.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(product);
    }

    @Override
    public Product archive(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        if (!product.canTransitionTo(ProductStatus.ARCHIVED)) {
            throw new ProductValidationException(
                    "Product cannot be archived from status " + product.getStatus());
        }

        product.setStatus(ProductStatus.ARCHIVED);
        Product saved = productRepository.save(product);
        try {
            eventPublisher.publish(new ProductArchivedEvent(productId, product.getSkuValue(), product.getName(), Instant.now()));
        } catch (Exception ignore) {
            // publishing must not break the use case
        }
        return saved;
    }

    @Override
    public Product activate(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        if (!product.canTransitionTo(ProductStatus.ACTIVE)) {
            throw new ProductValidationException(
                    "Product cannot be activated from status " + product.getStatus());
        }

        product.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(product);
    }

    @Override
    public List<Product> findByName(String name) {
        return productRepository.findByName(name);
    }

    @Override
    public Optional<Product> findActiveBySlug(Slug slug) {
        return productRepository.findBySlug(slug)
                .filter(product -> product.getStatus() == ProductStatus.ACTIVE);
    }

    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    public Optional<Product> findById(String productId) {
        return productRepository.findById(productId);
    }

    @Override
    public PageResult<Product> search(ProductSearchCriteria criteria) {
        return productRepository.search(criteria);
    }

    @Override
    public Product uploadImage(String productId, UploadProductImageCommand command) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        if (!ALLOWED_CONTENT_TYPES.contains(command.contentType())) {
            throw new InvalidProductImageException(
                    "Unsupported image content type: " + command.contentType());
        }
        if (command.content() == null || command.content().length > MAX_IMAGE_BYTES) {
            throw new InvalidProductImageException("Image must be at most 5 MB");
        }
        if (product.getImages().size() >= MAX_IMAGES_PER_PRODUCT) {
            throw new InvalidProductImageException(
                    "A product can have at most " + MAX_IMAGES_PER_PRODUCT + " images");
        }

        String objectKey = imageStorage.upload(
                command.content(), command.contentType(),
                "products/" + product.getSkuValue());

        boolean makePrimary = command.primary()
                || product.getImages().stream().noneMatch(ProductImage::isPrimary);
        if (makePrimary) {
            product.getImages().forEach(image -> image.setPrimary(false));
        }
        ProductImage image = new ProductImage(null, objectKey, command.altText(),
                command.position(), makePrimary);
        product.addImage(image);
        return productRepository.save(product);
    }

    @Override
    public Product updateImageMeta(String productId, Long primaryImageId,
                                   Map<Long, String> altTextByImageId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        if (primaryImageId != null) {
            try {
                product.markImageAsPrimary(primaryImageId);
            } catch (IllegalArgumentException e) {
                throw new ProductValidationException(e.getMessage());
            }
        }
        if (altTextByImageId != null) {
            altTextByImageId.forEach((imageId, altText) ->
                    product.getImages().stream()
                            .filter(image -> Objects.equals(image.getId(), imageId))
                            .findFirst()
                            .ifPresent(image -> image.setAltText(altText)));
        }
        return productRepository.save(product);
    }

    @Override
    public Product moveImage(String productId, Long imageId, int newPosition) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        List<ProductImage> ordered = new ArrayList<>(product.getImages());
        ordered.sort(Comparator.comparingInt(ProductImage::getPosition));
        int currentIndex = indexOf(ordered, imageId);
        if (currentIndex < 0) {
            throw new ProductValidationException("Image not found: " + imageId);
        }
        if (newPosition < 0 || newPosition >= ordered.size()) {
            throw new ProductValidationException("Invalid image position: " + newPosition);
        }
        if (currentIndex != newPosition) {
            ProductImage target = ordered.get(currentIndex);
            ordered.remove(currentIndex);
            ordered.add(newPosition, target);
            for (int i = 0; i < ordered.size(); i++) {
                ordered.get(i).setPosition(i);
            }
        }
        return productRepository.save(product);
    }

    private static int indexOf(List<ProductImage> images, Long imageId) {
        for (int i = 0; i < images.size(); i++) {
            if (Objects.equals(images.get(i).getId(), imageId)) {
                return i;
            }
        }
        return -1;
    }

    private Slug uniqueSlug(String base) {
        String candidate = base;
        int suffix = 2;
        while (productRepository.existsBySlug(new Slug(candidate))) {
            candidate = base + "-" + suffix++;
        }
        return new Slug(candidate);
    }

    private static String slugify(String name) {
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "product" : slug;
    }

    private static String sanitizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return description;
        }
        return DESCRIPTION_SANITIZER.sanitize(description);
    }

    private static final org.owasp.html.PolicyFactory DESCRIPTION_SANITIZER =
            org.owasp.html.Sanitizers.FORMATTING
                    .and(org.owasp.html.Sanitizers.BLOCKS)
                    .and(org.owasp.html.Sanitizers.LINKS);
}
