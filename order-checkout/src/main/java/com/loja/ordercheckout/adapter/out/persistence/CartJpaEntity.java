package com.loja.ordercheckout.adapter.out.persistence;

import com.loja.ordercheckout.domain.model.Cart;
import com.loja.ordercheckout.domain.model.CartLine;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA persistence entity for {@link Cart}.
 *
 * <p>Isolated in the adapter layer — the domain aggregate never carries
 * framework annotations. {@code version} backs optimistic locking and is
 * round-tripped both ways (see {@code docs/lessons.md} item 12); {@code
 * updatedAt} changes on every domain mutation, which keeps the parent row dirty
 * so a line-level change still bumps the version on flush.
 */
@Entity
@Table(name = "tb_cart")
public class CartJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36, unique = true)
    private String userId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tb_cart_line", joinColumns = @JoinColumn(name = "cart_id"))
    private List<CartLineEmbeddable> lines = new ArrayList<>();

    protected CartJpaEntity() { }

    public static CartJpaEntity fromDomain(Cart cart) {
        CartJpaEntity e = new CartJpaEntity();
        e.id = cart.getId();
        e.userId = cart.getUserId();
        e.version = cart.getVersion();
        e.updatedAt = cart.getUpdatedAt();
        e.lines = new ArrayList<>(cart.getLines().stream()
                .map(CartLineEmbeddable::fromDomain)
                .toList());
        return e;
    }

    public Cart toDomain() {
        return Cart.reconstitute(id, userId,
                lines.stream().map(CartLineEmbeddable::toDomain).toList(),
                version, updatedAt);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CartLineEmbeddable> getLines() { return lines; }

    @Embeddable
    public static class CartLineEmbeddable {

        @Column(name = "product_id", nullable = false, length = 36)
        private String productId;

        @Column(nullable = false)
        private int quantity;

        protected CartLineEmbeddable() { }

        public static CartLineEmbeddable fromDomain(CartLine line) {
            CartLineEmbeddable e = new CartLineEmbeddable();
            e.productId = line.productId();
            e.quantity = line.quantity();
            return e;
        }

        public CartLine toDomain() {
            return new CartLine(productId, quantity);
        }

        public String getProductId() { return productId; }
        public int getQuantity() { return quantity; }
    }
}
