package com.loja.productcatalog.domain.port.out;

/** Output port (driven port): blob storage for product images (S3-compatible). */
public interface ProductImageStoragePort {
    String upload(byte[] content, String contentType, String suggestedKeyPrefix);
    void delete(String objectKey);
    String publicUrlFor(String objectKey);
}
