package com.loja.ordercheckout.domain.model;

import com.loja.ordercheckout.domain.exception.CartLineNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root of the durable shopping cart. MVP keeps a single active cart
 * per {@code userId} holding a list of {@link CartLine} entries — at most one
 * line per product.
 *
 * <p>Prices and names are never frozen on the cart: the application resolves
 * them live from the catalog through ports at read time.
 *
 * <p>{@code version} backs JPA optimistic locking and is round-tripped by the
 * persistence mapper (see {@code docs/lessons.md} item 12). The updated
 * timestamp is bumped on every mutation.
 */
public final class Cart {

    private final String id;
    private final String userId;
    private final List<CartLine> lines = new ArrayList<>();
    private long version;
    private Instant updatedAt;

    private Cart(String id, String userId, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.updatedAt = updatedAt;
    }

    /**
     * New empty cart for the given owner.
     *
     * @param userId authenticated owner (never from the form alone)
     */
    public static Cart create(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        return new Cart(UUID.randomUUID().toString(), userId.trim(), Instant.now());
    }

    /**
     * Restore an exact persisted snapshot, including the optimistic-lock version.
     * Used by the JPA mapper.
     */
    public static Cart reconstitute(String id, String userId, List<CartLine> lines,
                                    long version, Instant updatedAt) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt is required");
        }
        Cart cart = new Cart(id, userId.trim(), updatedAt);
        cart.version = version;
        if (lines != null) {
            lines.forEach(cart.lines::add);
        }
        return cart;
    }

    /**
     * Add {@code quantity} of the given product. Adding a product that is already
     * on the cart increments the existing line's quantity (one line per product).
     *
     * @param productId target product (trimmed on the way in)
     * @param quantity  {@code >= 1}
     */
    public void add(String productId, int quantity) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        String product = productId.trim();
        int index = indexOf(product);
        if (index >= 0) {
            CartLine existing = lines.get(index);
            lines.set(index, new CartLine(product, existing.quantity() + quantity));
        } else {
            lines.add(new CartLine(product, quantity));
        }
        touch();
    }

    /**
     * Set the exact quantity for an existing line. A quantity of zero removes the
     * line; targeting a product that is not on the cart throws
     * {@link CartLineNotFoundException}.
     *
     * @param productId target product
     * @param quantity  {@code >= 0}
     */
    public void updateQuantity(String productId, int quantity) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be at least 0");
        }
        String product = productId.trim();
        int index = indexOf(product);
        if (index < 0) {
            throw new CartLineNotFoundException(product);
        }
        if (quantity == 0) {
            lines.remove(index);
        } else {
            lines.set(index, new CartLine(product, quantity));
        }
        touch();
    }

    /**
     * Remove the line for the given product. Idempotent: no-op when absent.
     */
    public void remove(String productId) {
        if (productId == null || productId.isBlank()) {
            return;
        }
        int index = indexOf(productId.trim());
        if (index >= 0) {
            lines.remove(index);
            touch();
        }
    }

    /** Remove every line. No-op (and no timestamp bump) when already empty. */
    public void clear() {
        if (!lines.isEmpty()) {
            lines.clear();
            touch();
        }
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CartLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    private int indexOf(String productId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).productId().equals(productId)) {
                return i;
            }
        }
        return -1;
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}
