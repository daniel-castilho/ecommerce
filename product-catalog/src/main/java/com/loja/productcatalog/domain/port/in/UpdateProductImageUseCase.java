package com.loja.productcatalog.domain.port.in;

import com.loja.productcatalog.domain.model.Product;
import java.util.Map;

/**
 * Input port: image management that happens after upload — setting the primary image,
 * editing alt text, and reordering (spec §8). Added for the admin UI (Step 7); the
 * primary-image invariant and reorder logic delegate to the {@code Product} domain object.
 */
public interface UpdateProductImageUseCase {
    Product updateImageMeta(String productId, Long primaryImageId, Map<Long, String> altTextByImageId);
    Product moveImage(String productId, Long imageId, int newPosition);
}
