package com.loja.productcatalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tb_product_image")
public class ProductImageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "alt_text", length = 200)
    private String altText;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected ProductImageJpaEntity() {}

    public ProductImageJpaEntity(Long id, ProductJpaEntity product, String objectKey,
                                 String altText, int position, boolean primary) {
        this.id = id;
        this.product = product;
        this.objectKey = objectKey;
        this.altText = altText;
        this.position = position;
        this.primary = primary;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProductJpaEntity getProduct() { return product; }
    public void setProduct(ProductJpaEntity product) { this.product = product; }

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }

    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}
