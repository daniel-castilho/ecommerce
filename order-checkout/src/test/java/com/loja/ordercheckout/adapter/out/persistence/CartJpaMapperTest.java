package com.loja.ordercheckout.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.loja.ordercheckout.domain.model.Cart;
import com.loja.ordercheckout.domain.model.CartLine;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CartJpaMapperTest {

    @Test
    void shouldRoundTripCartIncludingVersionAndTimestamp() {
        Instant updatedAt = Instant.parse("2026-08-08T10:00:00Z");
        Cart original = Cart.reconstitute("cart-1", "user-1",
                List.of(new CartLine("p1", 2), new CartLine("p2", 1)), 4L, updatedAt);

        Cart restored = CartJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getId()).isEqualTo("cart-1");
        assertThat(restored.getUserId()).isEqualTo("user-1");
        assertThat(restored.getVersion()).isEqualTo(4L);
        assertThat(restored.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(restored.getLines()).hasSize(2);
        assertThat(restored.getLines().get(0).productId()).isEqualTo("p1");
        assertThat(restored.getLines().get(0).quantity()).isEqualTo(2);
        assertThat(restored.getLines().get(1).productId()).isEqualTo("p2");
        assertThat(restored.getLines().get(1).quantity()).isEqualTo(1);
    }

    @Test
    void shouldRoundTripEmptyCart() {
        Instant updatedAt = Instant.parse("2026-08-08T10:00:00Z");
        Cart original = Cart.reconstitute("cart-1", "user-1", List.of(), 0L, updatedAt);

        Cart restored = CartJpaEntity.fromDomain(original).toDomain();

        assertThat(restored.getLines()).isEmpty();
        assertThat(restored.getVersion()).isZero();
        assertThat(restored.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
