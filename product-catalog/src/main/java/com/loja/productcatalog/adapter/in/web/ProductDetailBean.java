package com.loja.productcatalog.adapter.in.web;

import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductImage;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.in.GetProductDetailUseCase;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

/**
 * Public product detail bean (spec §10): resolves an ACTIVE product by slug and
 * exposes the image gallery + category names for the storefront detail page.
 * {@code @ViewScoped} so selecting a gallery thumbnail via postback keeps the
 * loaded product; the lookup itself goes through {@link GetProductDetailUseCase},
 * which never returns non-ACTIVE products.
 */
@Named("productDetailBean")
@ViewScoped
public class ProductDetailBean implements Serializable {

    @Inject
    private GetProductDetailUseCase getProductDetail;

    @Inject
    private CategoryRepositoryPort categoryRepository;

    @Inject
    private ProductImageStoragePort imageStorage;

    private Product product;
    private int selectedImageIndex;

    @PostConstruct
    void init() {
        String slug = FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap().get("slug");
        if (slug == null || slug.isBlank()) {
            return;
        }
        try {
            product = getProductDetail.findActiveBySlug(new Slug(slug)).orElse(null);
        } catch (IllegalArgumentException e) {
            product = null;
        }
    }

    public Product getProduct() {
        return product;
    }

    public boolean isNotFound() {
        return product == null;
    }

    public String getTitle() {
        return product == null ? "Product not found" : product.getName();
    }

    public String getCategoryNames() {
        if (product == null) {
            return "";
        }
        return product.getCategoryIds().stream()
                .map(categoryRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(category -> category.getName())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    public boolean isInStock() {
        return product != null && product.getStock() > 0;
    }

    public List<ProductImage> getGalleryImages() {
        if (product == null) {
            return List.of();
        }
        return product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getPosition))
                .toList();
    }

    public int getSelectedImageIndex() {
        return selectedImageIndex;
    }

    public String imageUrl(ProductImage image) {
        return imageStorage.publicUrlFor(image.getObjectKey());
    }

    public String getMainImageUrl() {
        List<ProductImage> gallery = getGalleryImages();
        if (gallery.isEmpty()) {
            return null;
        }
        int index = Math.min(Math.max(selectedImageIndex, 0), gallery.size() - 1);
        return imageStorage.publicUrlFor(gallery.get(index).getObjectKey());
    }

    public String getMainImageAlt() {
        List<ProductImage> gallery = getGalleryImages();
        if (gallery.isEmpty()) {
            return "";
        }
        int index = Math.min(Math.max(selectedImageIndex, 0), gallery.size() - 1);
        String altText = gallery.get(index).getAltText();
        return altText == null || altText.isBlank() ? product.getName() : altText;
    }

    public void selectImage(int index) {
        if (product != null && index >= 0 && index < getGalleryImages().size()) {
            selectedImageIndex = index;
        }
    }
}
