package com.loja.ordercheckout.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loja.ordercheckout.domain.exception.CartLineNotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CartTest {

    // ------------------------------------------------------------- create

    @Test
    void create_generatesIdAndEmptyLines() {
        Cart cart = Cart.create("user-1");

        assertThat(cart.getId()).isNotBlank();
        assertThat(cart.getUserId()).isEqualTo("user-1");
        assertThat(cart.getLines()).isEmpty();
        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.getVersion()).isZero();
        assertThat(cart.getUpdatedAt()).isNotNull();
    }

    @Test
    void create_blankUserId_throws() {
        assertThatThrownBy(() -> Cart.create(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cart.create("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------- add

    @Test
    void add_newProduct_addsLineWithQuantity() {
        Cart cart = Cart.create("user-1");

        cart.add("p1", 2);

        assertThat(cart.getLines()).hasSize(1);
        assertThat(cart.getLines().get(0).productId()).isEqualTo("p1");
        assertThat(cart.getLines().get(0).quantity()).isEqualTo(2);
        assertThat(cart.isEmpty()).isFalse();
    }

    @Test
    void add_existingProduct_incrementsQuantityOnSameLine() {
        Cart cart = Cart.create("user-1");
        cart.add("p1", 1);

        cart.add("p1", 3);

        assertThat(cart.getLines()).hasSize(1);
        assertThat(cart.getLines().get(0).quantity()).isEqualTo(4);
    }

    @Test
    void add_twoProducts_keepsTwoLines() {
        Cart cart = Cart.create("user-1");

        cart.add("p1", 1);
        cart.add("p2", 5);

        assertThat(cart.getLines()).extracting(CartLine::productId).containsExactly("p1", "p2");
    }

    @Test
    void add_sameProductWithWhitespace_mergesIntoSingleLine() {
        Cart cart = Cart.create("user-1");
        cart.add("p1", 1);

        cart.add("  p1  ", 2);

        assertThat(cart.getLines()).hasSize(1);
        assertThat(cart.getLines().get(0).quantity()).isEqualTo(3);
        assertThat(cart.getLines().get(0).productId()).isEqualTo("p1");
    }

    @Test
    void add_quantityLessThanOne_throws() {
        Cart cart = Cart.create("user-1");

        assertThatThrownBy(() -> cart.add("p1", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.add("p1", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.getLines()).isEmpty();
    }

    @Test
    void add_blankProductId_throws() {
        Cart cart = Cart.create("user-1");

        assertThatThrownBy(() -> cart.add(null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.add(" ", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------- updateQuantity

    @Test
    void updateQuantity_existingLine_setsExactQuantity() {
        Cart cart = Cart.create("user-1");
        cart.add("p1", 1);

        cart.updateQuantity("p1", 7);

        assertThat(cart.getLines().get(0).quantity()).isEqualTo(7);
        assertThat(cart.getLines()).hasSize(1);
    }

    @Test
    void updateQuantity_zero_removesTheLine() {
        Cart cart = Cart.create("user-1");
        cart.add("p1", 1);
        cart.add("p2", 2);

        cart.updateQuantity("p1", 0);

        assertThat(cart.getLines()).extracting(CartLine::productId).containsExactly("p2");
    }

    @Test
    void updateQuantity_absentLine_throws() {
        Cart cart = Cart.create("user-1");

        assertThatThrownBy(() -> cart.updateQuantity("missing", 2))
                .isInstanceOf(CartLineNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void updateQuantity_negativeQuantity_throws() {
        Cart cart = Cart.create("user-1");
        cart.add("p1", 1);

        assertThatThrownBy(() -> cart.updateQuantity("p1", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------- remove

    @Test
    void remove_existingLine_removesIt() {
        Cart cart = Cart.create("user-1");
        cart.add("p1", 1);
        cart.add("p2", 2);

        cart.remove("p1");

        assertThat(cart.getLines()).extracting(CartLine::productId).containsExactly("p2");
    }

    @Test
    void remove_absentLine_isIdempotentNoOp() {
        Cart cart = Cart.create("user-1");
        cart.add("p1", 1);

        cart.remove("missing");

        assertThat(cart.getLines()).hasSize(1);
    }

    // ------------------------------------------------------------- clear

    @Test
    void clear_removesAllLines() {
        Cart cart = Cart.create("user-1");
        cart.add("p1", 1);
        cart.add("p2", 2);

        cart.clear();

        assertThat(cart.getLines()).isEmpty();
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void clear_emptyCart_isNoOp() {
        Cart cart = Cart.create("user-1");

        cart.clear();

        assertThat(cart.isEmpty()).isTrue();
    }

    // ------------------------------------------------------------- reconstitute

    @Test
    void reconstitute_restoresExactSnapshotIncludingVersionAndTimestamp() {
        Instant updatedAt = Instant.parse("2026-08-08T10:00:00Z");
        List<CartLine> lines = List.of(new CartLine("p1", 2), new CartLine("p2", 1));

        Cart cart = Cart.reconstitute("cart-1", "user-1", lines, 4L, updatedAt);

        assertThat(cart.getId()).isEqualTo("cart-1");
        assertThat(cart.getUserId()).isEqualTo("user-1");
        assertThat(cart.getLines()).hasSize(2);
        assertThat(cart.getVersion()).isEqualTo(4L);
        assertThat(cart.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void reconstitute_invalidArguments_throw() {
        assertThatThrownBy(() -> Cart.reconstitute(null, "user-1", List.of(), 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cart.reconstitute("cart-1", "", List.of(), 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cart.reconstitute("cart-1", "user-1", List.of(), 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------- misc

    @Test
    void getLines_isUnmodifiable() {
        Cart cart = Cart.create("user-1");
        cart.add("p1", 1);

        assertThatThrownBy(() -> cart.getLines().add(new CartLine("p2", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mutations_bumpUpdatedAt() {
        Cart cart = Cart.create("user-1");
        Instant created = cart.getUpdatedAt();

        cart.add("p1", 1);

        assertThat(cart.getUpdatedAt()).isAfterOrEqualTo(created);
    }
}
