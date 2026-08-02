package com.loja.productcatalog.domain.model;

public class ProductImage {

    private final Long id;
    private final String objectKey;
    private String altText;
    private int position;
    private boolean primary;

    public ProductImage(Long id, String objectKey, String altText, int position, boolean primary) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Image object key is required");
        }
        this.id = id;
        this.objectKey = objectKey;
        this.altText = altText;
        this.position = position;
        this.primary = primary;
    }

    public Long getId() { return id; }
    public String getObjectKey() { return objectKey; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}
