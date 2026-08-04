package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.loja.admindashboard.domain.port.in.UpdateProductForAdminUseCase;
import com.loja.productcatalog.application.dto.UpdateProductCommand;
import com.loja.productcatalog.application.dto.UploadProductImageCommand;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.port.in.ActivateProductUseCase;
import com.loja.productcatalog.domain.port.in.FindProductByIdUseCase;
// image use-cases are routed through UpdateProductForAdminUseCase now
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("productEditBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class ProductEditBean implements Serializable {

    @Inject
    private UpdateProductForAdminUseCase updateProductForAdminUseCase;

    @Inject
    private FindProductByIdUseCase findProductByIdUseCase;

    @Inject
    private CategoryRepositoryPort categoryRepository;

    @Inject
    private ProductImageStoragePort imageStorage;

    @Inject
    private ActivateProductUseCase activateProductUseCase;

    @Inject
    private com.loja.productcatalog.domain.port.in.ArchiveProductUseCase archiveProductUseCase;



    private String productId;
    private String sku;
    private String name;
    private String slug;
    private String shortDescription;
    private String description;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private int stock;
    private Integer weightGrams;
    private String metaTitle;
    private String metaDescription;
    private Set<Long> selectedCategoryIds = new HashSet<>();
    private List<Category> categories = List.of();
    private Product currentProduct;
    private jakarta.servlet.http.Part file;
    private String uploadAltText;
    private boolean uploadPrimary;
    private Long primaryImageId;

    void setUpdateProductForAdminUseCase(UpdateProductForAdminUseCase updateProductForAdminUseCase) {
        this.updateProductForAdminUseCase = updateProductForAdminUseCase;
    }

    void setActivateProductUseCase(ActivateProductUseCase activateProductUseCase) {
        this.activateProductUseCase = activateProductUseCase;
    }

    void setArchiveProductUseCase(com.loja.productcatalog.domain.port.in.ArchiveProductUseCase archiveProductUseCase) {
        this.archiveProductUseCase = archiveProductUseCase;
    }

    void setCurrentProduct(Product currentProduct) {
        this.currentProduct = currentProduct;
    }

    @PostConstruct
    void load() {
        String param = FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap().get("productId");
        if (param != null && !param.isBlank()) {
            productId = param;
        }
        categories = categoryRepository.findAllActive();
        if (productId != null) {
            Optional<Product> product = findProductByIdUseCase.findById(productId);
            currentProduct = product.orElse(null);
            if (currentProduct != null) {
                prefillFromProduct();
            }
        }
    }

    public String submit() {
        try {
            Product updated = updateProductForAdminUseCase.update(productId, buildUpdateCommand());
            if (updated == null) {
                return null;
            }
            FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
            return "/admin-dashboard/products/list.xhtml?faces-redirect=true";
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Cannot update product", e.getMessage()));
            return null;
        }
    }

    public String uploadImage() {
        try {
            if (file == null) {
                    FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("image.upload.no_file"),
                            FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("image.upload.no_file")
                        ));
                return null;
            }
            byte[] content = file.getInputStream().readAllBytes();
            int nextPosition = currentProduct.getImages().stream()
                    .mapToInt(img -> img.getPosition()).max().orElse(-1) + 1;
                updateProductForAdminUseCase.uploadImage(productId, new UploadProductImageCommand(
                    content, file.getContentType(), uploadAltText, nextPosition, uploadPrimary));
            uploadAltText = null;
            uploadPrimary = false;
            file = null;
                String uploaded = FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("image.uploaded");
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, uploaded, uploaded));
            // reload product
            Optional<Product> product = findProductByIdUseCase.findById(productId);
            currentProduct = product.orElse(null);
            if (currentProduct != null) {
                prefillFromProduct();
            }
            return null;
        } catch (Exception e) {
                String template = FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("image.upload.failed");
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, template.replace("{0}", e.getMessage()), template.replace("{0}", e.getMessage())));
            return null;
        }
    }

    public String applyImageChanges() {
        try {
            Map<Long, String> altTextByImageId = new HashMap<>();
            currentProduct.getImages().forEach(image -> altTextByImageId.put(image.getId(), image.getAltText()));
            updateProductForAdminUseCase.updateImageMeta(productId, primaryImageId, altTextByImageId);
                String updated = FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("images.updated");
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, updated, updated));
            Optional<Product> product = findProductByIdUseCase.findById(productId);
            currentProduct = product.orElse(null);
            if (currentProduct != null) {
                prefillFromProduct();
            }
            return null;
        } catch (Exception e) {
                String template = FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("image.update.failed");
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, template.replace("{0}", e.getMessage()), template.replace("{0}", e.getMessage())));
            return null;
        }
    }

    public String moveUp(com.loja.productcatalog.domain.model.ProductImage image) {
        return move(image, image.getPosition() - 1);
    }

    public String moveDown(com.loja.productcatalog.domain.model.ProductImage image) {
        return move(image, image.getPosition() + 1);
    }

    private String move(com.loja.productcatalog.domain.model.ProductImage image, int newPosition) {
        try {
            updateProductForAdminUseCase.moveImage(productId, image.getId(), newPosition);
                String message = FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("image.reordered");
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, message, message));
            Optional<Product> product = findProductByIdUseCase.findById(productId);
            currentProduct = product.orElse(null);
            if (currentProduct != null) {
                prefillFromProduct();
            }
            return null;
        } catch (Exception e) {
                String template = FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("image.reorder.failed");
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, template.replace("{0}", e.getMessage()), template.replace("{0}", e.getMessage())));
            return null;
        }
    }

    public String imageUrl(com.loja.productcatalog.domain.model.ProductImage image) {
        return imageStorage.publicUrlFor(image.getObjectKey());
    }

    private UpdateProductCommand buildUpdateCommand() {
        return new UpdateProductCommand(
                name,
                slug,
                shortDescription,
                description,
                stock,
                weightGrams,
                metaTitle,
                metaDescription,
                selectedCategoryIds);
    }

    private void prefillFromProduct() {
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
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSku() { return sku; }

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

    public List<Category> getCategories() { return categories; }
    public Product getCurrentProduct() { return currentProduct; }

    public jakarta.servlet.http.Part getFile() { return file; }
    public void setFile(jakarta.servlet.http.Part file) { this.file = file; }

    public String getUploadAltText() { return uploadAltText; }
    public void setUploadAltText(String uploadAltText) { this.uploadAltText = uploadAltText; }

    public boolean isUploadPrimary() { return uploadPrimary; }
    public void setUploadPrimary(boolean uploadPrimary) { this.uploadPrimary = uploadPrimary; }

    public Long getPrimaryImageId() { return primaryImageId; }
    public void setPrimaryImageId(Long primaryImageId) { this.primaryImageId = primaryImageId; }

    public boolean canMoveUp(com.loja.productcatalog.domain.model.ProductImage image) {
        return image.getPosition() > 0;
    }

    public boolean canMoveDown(com.loja.productcatalog.domain.model.ProductImage image) {
        if (currentProduct == null || currentProduct.getImages().isEmpty()) return false;
        int max = currentProduct.getImages().stream().mapToInt(i -> i.getPosition()).max().orElse(0);
        return image.getPosition() < max;
    }

    public String deactivate() {
        try {
            archiveProductUseCase.archive(productId);
            FacesContext ctx = FacesContext.getCurrentInstance();
            String msg = ctx.getApplication().getResourceBundle(ctx, "msg").getString("product.deactivated");
            ctx.getExternalContext().getFlash().setKeepMessages(true);
            ctx.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, msg, msg));
            return "/admin-dashboard/products/list.xhtml?faces-redirect=true";
        } catch (Exception e) {
            String template = FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("product.deactivate.failed");
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, template.replace("{0}", e.getMessage()), template.replace("{0}", e.getMessage())));
            return null;
        }
    }

    public boolean canActivate() {
        return currentProduct != null && currentProduct.getStatus() == ProductStatus.ARCHIVED;
    }

    public boolean canDeactivate() {
        return currentProduct != null && currentProduct.canTransitionTo(ProductStatus.ARCHIVED);
    }

    public String activate() {
        try {
            activateProductUseCase.activate(productId);
            FacesContext ctx = FacesContext.getCurrentInstance();
            String msg = ctx.getApplication().getResourceBundle(ctx, "msg").getString("product.activated");
            ctx.getExternalContext().getFlash().setKeepMessages(true);
            ctx.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, msg, msg));
            return "/admin-dashboard/products/list.xhtml?faces-redirect=true";
        } catch (Exception e) {
            String template = FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "msg").getString("product.activate.failed");
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, template.replace("{0}", e.getMessage()), template.replace("{0}", e.getMessage())));
            return null;
        }
    }
}
