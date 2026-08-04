package com.loja.admindashboard.adapter.in.web;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.loja.admindashboard.domain.port.in.CreateProductForAdminUseCase;
import com.loja.productcatalog.application.dto.CreateProductCommand;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.shared.domain.Money;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("productCreateBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class ProductCreateBean implements Serializable {

    @Inject
    private CreateProductForAdminUseCase createProductForAdminUseCase;

    @Inject
    private CategoryRepositoryPort categoryRepository;

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

    void setCreateProductForAdminUseCase(CreateProductForAdminUseCase createProductForAdminUseCase) {
        this.createProductForAdminUseCase = createProductForAdminUseCase;
    }

    @PostConstruct
    void load() {
        categories = categoryRepository.findAllActive();
    }

    public String submit() {
        Product created = createProductForAdminUseCase.create(buildCreateCommand());
        if (created == null) {
            return null;
        }
        return "/admin-dashboard/products/list.xhtml?faces-redirect=true";
    }

    private CreateProductCommand buildCreateCommand() {
        return new CreateProductCommand(
                sku,
                name,
                slug,
                shortDescription,
                description,
                price != null ? new Money(price) : null,
                compareAtPrice != null ? new Money(compareAtPrice) : null,
                stock,
                weightGrams,
                metaTitle,
                metaDescription,
                selectedCategoryIds);
    }

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

    public List<Category> getCategories() { return categories; }
}
