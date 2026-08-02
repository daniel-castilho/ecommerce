package com.loja.productcatalog.adapter.in.web;

import com.loja.productcatalog.application.dto.CreateProductCommand;
import com.loja.productcatalog.application.dto.UpdateProductCommand;
import com.loja.productcatalog.application.dto.UploadProductImageCommand;
import com.loja.productcatalog.application.service.CategoryTreeCache;
import com.loja.productcatalog.domain.exception.DuplicateSkuException;
import com.loja.productcatalog.domain.exception.InvalidProductImageException;
import com.loja.productcatalog.domain.exception.ProductValidationException;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.port.in.ArchiveProductUseCase;
import com.loja.productcatalog.domain.port.in.CreateProductUseCase;
import com.loja.productcatalog.domain.port.in.PublishProductUseCase;
import com.loja.productcatalog.domain.port.in.SearchProductsUseCase;
import com.loja.productcatalog.domain.port.in.UpdateProductImageUseCase;
import com.loja.productcatalog.domain.port.in.UpdateProductUseCase;
import com.loja.productcatalog.domain.port.in.UploadProductImageUseCase;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import com.loja.shared.domain.Money;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin-only JSF bean for product CRUD, image management (upload, alt text, primary
 * image, reorder) and the publish/archive workflow (spec §8). Thin adapter: form
 * binding + exception-to-{@link FacesMessage} translation only. {@code @ViewScoped}
 * because image management is multi-step within one conversation; state (the edited
 * product id) would be lost between postbacks with {@code @RequestScoped}.
 */
@Named("manageProductBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class ManageProductBean implements Serializable {

    @Inject
    private CreateProductUseCase createProductUseCase;

    @Inject
    private UpdateProductUseCase updateProductUseCase;

    @Inject
    private PublishProductUseCase publishProductUseCase;

    @Inject
    private ArchiveProductUseCase archiveProductUseCase;

    @Inject
    private SearchProductsUseCase searchProductsUseCase;

    @Inject
    private UploadProductImageUseCase uploadProductImageUseCase;

    @Inject
    private UpdateProductImageUseCase updateProductImageUseCase;

    @Inject
    private ProductImageStoragePort imageStorage;

    @Inject
    private CategoryRepositoryPort categoryRepository;

    @Inject
    private CategoryTreeCache categoryTreeCache;

    private List<Product> products = List.of();
    private List<Category> categories = List.of();

    private Product currentProduct;
    private String productId;

    @NotBlank(message = "SKU is required")
    @Pattern(regexp = "^[A-Z0-9]+(-[A-Z0-9]+)*$", message = "Invalid SKU format")
    @Size(max = 64, message = "SKU must be at most 64 characters")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Product name must be at most 200 characters")
    private String name;

    @Size(max = 160, message = "Slug must be at most 160 characters")
    private String slug;

    @Size(max = 500, message = "Short description must be at most 500 characters")
    private String shortDescription;

    @Size(max = 10000, message = "Description must be at most 10000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Compare-at price must be greater than zero")
    private BigDecimal compareAtPrice;

    @Min(value = 0, message = "Stock cannot be negative")
    private int stock;

    @Min(value = 0, message = "Weight cannot be negative")
    private Integer weightGrams;

    @Size(max = 200, message = "Meta title must be at most 200 characters")
    private String metaTitle;

    @Size(max = 500, message = "Meta description must be at most 500 characters")
    private String metaDescription;
    private Set<Long> selectedCategoryIds = new HashSet<>();

    private Part file;
    private String uploadAltText;
    private boolean uploadPrimary;
    private Long primaryImageId;

    @PostConstruct
    void load() {
        String param = FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap().get("productId");
        if (param != null && !param.isBlank()) {
            productId = param;
        }
        refreshAll();
        prefillFromProduct();
    }

    public String submit() {
        return isEditMode() ? save() : create();
    }

    public String create() {
        try {
            Product created = createProductUseCase.create(buildCreateCommand());
            FacesContext ctx = FacesContext.getCurrentInstance();
            ctx.getExternalContext().getFlash().setKeepMessages(true);
            return "/product-catalog/manageProduct.xhtml?faces-redirect=true&productId=" + created.getId();
        } catch (DuplicateSkuException e) {
            FacesContext.getCurrentInstance().addMessage("productForm:sku",
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Duplicate SKU", e.getMessage()));
            return null;
        } catch (IllegalArgumentException | ProductValidationException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Cannot create product", e.getMessage());
            return null;
        }
    }

    public String save() {
        try {
            updateProductUseCase.update(productId, buildUpdateCommand());
            addGlobal(FacesMessage.SEVERITY_INFO, "Product updated", "Changes saved");
            refreshAll();
            return null;
        } catch (ProductValidationException | IllegalArgumentException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Cannot update product", e.getMessage());
            return null;
        }
    }

    public String uploadImage() {
        try {
            if (file == null) {
                addGlobal(FacesMessage.SEVERITY_ERROR, "No file selected", "Choose an image to upload");
                return null;
            }
            byte[] content = file.getInputStream().readAllBytes();
            int nextPosition = currentProduct.getImages().stream()
                    .mapToInt(ProductImage::getPosition).max().orElse(-1) + 1;
            uploadProductImageUseCase.uploadImage(productId, new UploadProductImageCommand(
                    content, file.getContentType(), uploadAltText, nextPosition, uploadPrimary));
            uploadAltText = null;
            uploadPrimary = false;
            file = null;
            addGlobal(FacesMessage.SEVERITY_INFO, "Image uploaded", "Image added to the product");
            refreshAll();
            return null;
        } catch (InvalidProductImageException | ProductValidationException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Image rejected", e.getMessage());
            return null;
        } catch (IOException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Upload failed", "Could not read the selected file");
            return null;
        }
    }

    public String applyImageChanges() {
        try {
            Map<Long, String> altTextByImageId = new HashMap<>();
            currentProduct.getImages().forEach(image -> altTextByImageId.put(image.getId(), image.getAltText()));
            updateProductImageUseCase.updateImageMeta(productId, primaryImageId, altTextByImageId);
            addGlobal(FacesMessage.SEVERITY_INFO, "Images updated", "Primary image and alt text saved");
            refreshAll();
            return null;
        } catch (ProductValidationException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Cannot update images", e.getMessage());
            return null;
        }
    }

    public String moveUp(ProductImage image) {
        return move(image, image.getPosition() - 1);
    }

    public String moveDown(ProductImage image) {
        return move(image, image.getPosition() + 1);
    }

    private String move(ProductImage image, int newPosition) {
        try {
            updateProductImageUseCase.moveImage(productId, image.getId(), newPosition);
            addGlobal(FacesMessage.SEVERITY_INFO, "Image reordered", "Image order saved");
            refreshAll();
            return null;
        } catch (ProductValidationException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Cannot reorder images", e.getMessage());
            return null;
        }
    }

    public String publish() {
        try {
            publishProductUseCase.publish(productId);
            addGlobal(FacesMessage.SEVERITY_INFO, "Product published", "Product is now live in the catalog");
            refreshAll();
            return null;
        } catch (ProductValidationException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Cannot publish", e.getMessage());
            return null;
        }
    }

    public String archive() {
        try {
            archiveProductUseCase.archive(productId);
            FacesContext ctx = FacesContext.getCurrentInstance();
            ctx.getExternalContext().getFlash().setKeepMessages(true);
            return "/product-catalog/manageProduct.xhtml?faces-redirect=true";
        } catch (ProductValidationException e) {
            addGlobal(FacesMessage.SEVERITY_ERROR, "Cannot archive", e.getMessage());
            return null;
        }
    }

    public String newProduct() {
        productId = null;
        currentProduct = null;
        clearForm();
        return null;
    }

    public boolean canPublish() {
        return currentProduct != null && currentProduct.canTransitionTo(ProductStatus.ACTIVE);
    }

    public boolean canArchive() {
        return currentProduct != null && currentProduct.canTransitionTo(ProductStatus.ARCHIVED);
    }

    public boolean canMoveUp(ProductImage image) {
        return currentProduct != null && image != null && image.getPosition() > 0;
    }

    public boolean canMoveDown(ProductImage image) {
        return currentProduct != null && image != null
                && image.getPosition() < currentProduct.getImages().size() - 1;
    }

    public String imageUrl(ProductImage image) {
        return imageStorage.publicUrlFor(image.getObjectKey());
    }

    private CreateProductCommand buildCreateCommand() {
        return new CreateProductCommand(sku, name, slug, shortDescription, description,
                new Money(price), compareAtPrice != null ? new Money(compareAtPrice) : null,
                stock, weightGrams, metaTitle, metaDescription, selectedCategoryIds);
    }

    private UpdateProductCommand buildUpdateCommand() {
        return new UpdateProductCommand(name, slug, shortDescription, description,
                stock, weightGrams, metaTitle, metaDescription, selectedCategoryIds);
    }

    private void refreshAll() {
        products = searchProductsUseCase.findAll();
        categories = categoryTreeCache.getOrLoad(categoryRepository::findAll);
        if (productId != null) {
            currentProduct = products.stream()
                    .filter(p -> p.getId().equals(productId))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void prefillFromProduct() {
        if (currentProduct == null) {
            return;
        }
        sku = currentProduct.getSkuValue();
        name = currentProduct.getName();
        slug = currentProduct.getSlugValue();
        shortDescription = currentProduct.getShortDescription();
        description = currentProduct.getDescription();
        price = currentProduct.getPrice().getAmount();
        compareAtPrice = currentProduct.getCompareAtPrice() != null
                ? currentProduct.getCompareAtPrice().getAmount() : null;
        stock = currentProduct.getStock();
        weightGrams = currentProduct.getWeightGrams();
        metaTitle = currentProduct.getMetaTitle();
        metaDescription = currentProduct.getMetaDescription();
        selectedCategoryIds = new HashSet<>(currentProduct.getCategoryIds());
        primaryImageId = currentProduct.getImages().stream()
                .filter(ProductImage::isPrimary)
                .map(ProductImage::getId)
                .findFirst()
                .orElse(null);
    }

    private void clearForm() {
        sku = null;
        name = null;
        slug = null;
        shortDescription = null;
        description = null;
        price = null;
        compareAtPrice = null;
        stock = 0;
        weightGrams = null;
        metaTitle = null;
        metaDescription = null;
        selectedCategoryIds = new HashSet<>();
        uploadAltText = null;
        uploadPrimary = false;
        file = null;
        primaryImageId = null;
    }

    private static void addGlobal(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, summary, detail));
    }

    public List<Product> getProducts() { return products; }
    public List<Category> getCategories() { return categories; }
    public Product getCurrentProduct() { return currentProduct; }
    public boolean isEditMode() { return currentProduct != null; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCompareAtPrice() { return compareAtPrice; }
    public void setCompareAtPrice(BigDecimal compareAtPrice) { this.compareAtPrice = compareAtPrice; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public Integer getWeightGrams() { return weightGrams; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }
    public String getMetaTitle() { return metaTitle; }
    public void setMetaTitle(String metaTitle) { this.metaTitle = metaTitle; }
    public String getMetaDescription() { return metaDescription; }
    public void setMetaDescription(String metaDescription) { this.metaDescription = metaDescription; }
    public Set<Long> getSelectedCategoryIds() { return selectedCategoryIds; }
    public void setSelectedCategoryIds(Set<Long> selectedCategoryIds) { this.selectedCategoryIds = selectedCategoryIds; }

    public Part getFile() { return file; }
    public void setFile(Part file) { this.file = file; }
    public String getUploadAltText() { return uploadAltText; }
    public void setUploadAltText(String uploadAltText) { this.uploadAltText = uploadAltText; }
    public boolean isUploadPrimary() { return uploadPrimary; }
    public void setUploadPrimary(boolean uploadPrimary) { this.uploadPrimary = uploadPrimary; }
    public Long getPrimaryImageId() { return primaryImageId; }
    public void setPrimaryImageId(Long primaryImageId) { this.primaryImageId = primaryImageId; }
}
