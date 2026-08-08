package com.loja.wishlist.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.loja.wishlist.application.dto.ProductSnapshot;
import com.loja.wishlist.application.dto.WishlistItemDTO;
import com.loja.wishlist.domain.exception.DuplicateWishlistItemException;
import com.loja.wishlist.domain.exception.ProductNotAvailableException;
import com.loja.wishlist.domain.model.WishlistItem;
import com.loja.wishlist.domain.port.in.AddToWishlistUseCase;
import com.loja.wishlist.domain.port.in.ListMyWishlistUseCase;
import com.loja.wishlist.domain.port.in.RemoveFromWishlistUseCase;
import com.loja.wishlist.domain.port.out.ProductLookupPort;
import com.loja.wishlist.domain.port.out.WishlistRepositoryPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Application service implementing every wishlist use case.
 *
 * <p>Business rules that only depend on {@link WishlistItem} live on the
 * aggregate. Rules that consult a port — product ACTIVE check, uniqueness,
 * persistence — live here.
 */
@ApplicationScoped
@Transactional
public class WishlistApplicationService implements
        AddToWishlistUseCase,
        RemoveFromWishlistUseCase,
        ListMyWishlistUseCase {

    private final WishlistRepositoryPort wishlistRepository;
    private final ProductLookupPort productLookup;

    @Inject
    public WishlistApplicationService(WishlistRepositoryPort wishlistRepository,
                                      ProductLookupPort productLookup) {
        this.wishlistRepository = wishlistRepository;
        this.productLookup = productLookup;
    }

    @Override
    public String add(String userId, String productId) {
        requireNonBlank(userId, "userId");
        requireNonBlank(productId, "productId");

        if (productLookup.findActiveById(productId).isEmpty()) {
            throw new ProductNotAvailableException(productId);
        }

        Optional<WishlistItem> existing = wishlistRepository.findByUserAndProduct(userId, productId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        WishlistItem item = WishlistItem.create(userId, productId);
        try {
            return wishlistRepository.save(item).getId();
        } catch (DuplicateWishlistItemException e) {
            // Concurrent insert race: treat as idempotent success.
            return wishlistRepository.findByUserAndProduct(userId, productId)
                    .map(WishlistItem::getId)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    public void remove(String userId, String productId) {
        requireNonBlank(userId, "userId");
        requireNonBlank(productId, "productId");
        wishlistRepository.deleteByUserAndProduct(userId, productId);
    }

    @Override
    public List<WishlistItemDTO> list(String userId) {
        requireNonBlank(userId, "userId");
        List<WishlistItem> items = wishlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<WishlistItemDTO> result = new ArrayList<>(items.size());
        for (WishlistItem item : items) {
            result.add(toDto(item));
        }
        return result;
    }

    @Override
    public boolean contains(String userId, String productId) {
        requireNonBlank(userId, "userId");
        requireNonBlank(productId, "productId");
        return wishlistRepository.exists(userId, productId);
    }

    private WishlistItemDTO toDto(WishlistItem item) {
        Optional<ProductSnapshot> snapshot = productLookup.findActiveById(item.getProductId());
        if (snapshot.isPresent()) {
            ProductSnapshot product = snapshot.get();
            return new WishlistItemDTO(
                    item.getId(),
                    item.getProductId(),
                    product.name(),
                    product.slug(),
                    product.price(),
                    product.imageUrl(),
                    item.getCreatedAt());
        }
        // Product no longer ACTIVE / removed: still show the row with fallback labels.
        return new WishlistItemDTO(
                item.getId(),
                item.getProductId(),
                "Unavailable product",
                null,
                null,
                null,
                item.getCreatedAt());
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
