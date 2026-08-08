package com.loja.productcatalog.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.loja.productcatalog.domain.exception.ProductValidationException;
import com.loja.shared.domain.Money;

public class Product {

    private static final int NAME_MAX_LENGTH = 200;
    private static final Map<ProductStatus, Set<ProductStatus>> ALLOWED_TRANSITIONS = Map.of(
            ProductStatus.DRAFT, Set.of(ProductStatus.ACTIVE, ProductStatus.INACTIVE, ProductStatus.ARCHIVED),
            ProductStatus.ACTIVE, Set.of(ProductStatus.INACTIVE, ProductStatus.DRAFT, ProductStatus.ARCHIVED),
            ProductStatus.INACTIVE, Set.of(ProductStatus.ACTIVE, ProductStatus.DRAFT, ProductStatus.ARCHIVED),
            ProductStatus.ARCHIVED, Set.of(ProductStatus.ACTIVE));

    private final String id;
    private final Sku sku;
    private Slug slug;
    private Long version;
    private String name;
    private String shortDescription;
    private String description;
    private final Money price;
    private final Money compareAtPrice;
    private Money costPrice;
    private int stock;
    private ProductStatus status;
    private Integer weightGrams;
    private String metaTitle;
    private String metaDescription;
    private final Set<Long> categoryIds;
    private final List<ProductImage> images;

    public Product(String id, Sku sku, Slug slug, String name, String shortDescription, String description,
                   Money price, Money compareAtPrice, int stock, ProductStatus status,
                   Integer weightGrams, String metaTitle, String metaDescription,
                   Set<Long> categoryIds, List<ProductImage> images) {
        this(id, sku, slug, name, shortDescription, description, price, compareAtPrice, null,
                stock, status, weightGrams, metaTitle, metaDescription, categoryIds, images);
    }

    public Product(String id, Sku sku, Slug slug, String name, String shortDescription, String description,
                   Money price, Money compareAtPrice, Money costPrice, int stock, ProductStatus status,
                   Integer weightGrams, String metaTitle, String metaDescription,
                   Set<Long> categoryIds, List<ProductImage> images) {
        if (sku == null) {
            throw new IllegalArgumentException("SKU is required");
        }
        if (slug == null) {
            throw new IllegalArgumentException("Slug is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Product name exceeds " + NAME_MAX_LENGTH + " characters");
        }
        if (price == null) {
            throw new IllegalArgumentException("Price is required");
        }
        if (price.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (compareAtPrice != null && compareAtPrice.getAmount().compareTo(price.getAmount()) <= 0) {
            throw new IllegalArgumentException("Compare-at price must be greater than the price");
        }
        if (costPrice != null && costPrice.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost price cannot be negative");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        this.id = id;
        this.sku = sku;
        this.slug = slug;
        this.name = name;
        this.shortDescription = shortDescription;
        this.description = description;
        this.price = price;
        this.compareAtPrice = compareAtPrice;
        this.costPrice = costPrice;
        this.stock = stock;
        this.status = status != null ? status : ProductStatus.DRAFT;
        this.weightGrams = weightGrams;
        this.metaTitle = metaTitle;
        this.metaDescription = metaDescription;
        this.categoryIds = categoryIds != null ? new HashSet<>(categoryIds) : new HashSet<>();
        this.images = images != null ? new ArrayList<>(images) : new ArrayList<>();
    }

    public void reserveStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (quantity > stock) {
            throw new IllegalStateException("Insufficient stock for product: " + name);
        }
        this.stock -= quantity;
    }

    public boolean canTransitionTo(ProductStatus target) {
        return target != null && target != status
                && ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(target);
    }

    public void validateForPublishing() {
        List<String> errors = new ArrayList<>();
        if (images.isEmpty()) {
            errors.add("at least one image is required");
        }
        if (images.stream().anyMatch(image -> image.getAltText() == null || image.getAltText().isBlank())) {
            errors.add("every image needs alt text");
        }
        if (categoryIds.isEmpty()) {
            errors.add("at least one category is required");
        }
        if (!errors.isEmpty()) {
            throw new ProductValidationException(String.join("; ", errors));
        }
    }

    public void markImageAsPrimary(Long imageId) {
        boolean found = images.stream().anyMatch(image -> Objects.equals(image.getId(), imageId));
        if (!found) {
            throw new IllegalArgumentException("Image not found: " + imageId);
        }
        images.forEach(image -> image.setPrimary(Objects.equals(image.getId(), imageId)));
    }

    public void removeImage(Long imageId) {
        ProductImage removed = images.stream()
                .filter(image -> Objects.equals(image.getId(), imageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));
        boolean wasPrimary = removed.isPrimary();
        images.remove(removed);
        if (wasPrimary && !images.isEmpty()) {
            ProductImage next = images.stream()
                    .min(Comparator.comparingInt(ProductImage::getPosition))
                    .orElseThrow();
            next.setPrimary(true);
        }
    }

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Sku getSku() { return sku; }
    public Slug getSlug() { return slug; }
    public String getName() { return name; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public Money getPrice() { return price; }
    public Money getCompareAtPrice() { return compareAtPrice; }
    public Money getCostPrice() { return costPrice; }

    public void setCostPrice(Money costPrice) {
        if (costPrice != null && costPrice.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost price cannot be negative");
        }
        this.costPrice = costPrice;
    }

    /**
     * Gross profit margin over the given revenue, as a percentage rounded to two
     * decimals: {@code (revenue - costPrice * unitsSold) / revenue * 100}. Returns
     * {@code null} when no cost price is recorded, no units were sold or revenue
     * is zero/absent — a margin cannot be derived from that data.
     */
    public BigDecimal profitMargin(Money revenue, long unitsSold) {
        if (costPrice == null || revenue == null || unitsSold <= 0L
                || revenue.getAmount().signum() == 0) {
            return null;
        }
        BigDecimal totalCost = costPrice.getAmount().multiply(BigDecimal.valueOf(unitsSold));
        return revenue.getAmount().subtract(totalCost)
                .divide(revenue.getAmount(), 6, RoundingMode.HALF_EVEN)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_EVEN);
    }
    public int getStock() { return stock; }
    public ProductStatus getStatus() { return status; }
    public Integer getWeightGrams() { return weightGrams; }
    public String getMetaTitle() { return metaTitle; }
    public String getMetaDescription() { return metaDescription; }
    public Set<Long> getCategoryIds() { return categoryIds; }
    public List<ProductImage> getImages() { return images; }

    public String getSkuValue() { return sku.getValue(); }
    public String getSlugValue() { return slug.getValue(); }

    public void setStatus(ProductStatus status) { this.status = status; }
    public void setStock(int stock) { this.stock = stock; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public void setDescription(String description) { this.description = description; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }
    public void setMetaTitle(String metaTitle) { this.metaTitle = metaTitle; }
    public void setMetaDescription(String metaDescription) { this.metaDescription = metaDescription; }
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        this.name = name;
    }

    public void addCategory(Long categoryId) {
        this.categoryIds.add(categoryId);
    }

    public void removeCategory(Long categoryId) {
        this.categoryIds.remove(categoryId);
    }

    public void replaceCategories(Set<Long> categoryIds) {
        this.categoryIds.clear();
        if (categoryIds != null) {
            this.categoryIds.addAll(categoryIds);
        }
    }

    public void changeSlug(Slug slug) {
        if (slug == null) {
            throw new IllegalArgumentException("Slug is required");
        }
        this.slug = slug;
    }

    public void addImage(ProductImage image) {
        this.images.add(image);
    }
}
