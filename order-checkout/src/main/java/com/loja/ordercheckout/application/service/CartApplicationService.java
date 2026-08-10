package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.application.dto.CartLineView;
import com.loja.ordercheckout.application.dto.CartView;
import com.loja.ordercheckout.application.dto.ProductSnapshot;
import com.loja.ordercheckout.domain.exception.CartLineNotFoundException;
import com.loja.ordercheckout.domain.exception.CartProductNotAvailableException;
import com.loja.ordercheckout.domain.model.Cart;
import com.loja.ordercheckout.domain.model.CartLine;
import com.loja.ordercheckout.domain.port.in.AddToCartUseCase;
import com.loja.ordercheckout.domain.port.in.ClearCartUseCase;
import com.loja.ordercheckout.domain.port.in.GetCartUseCase;
import com.loja.ordercheckout.domain.port.in.MergeGuestCartUseCase;
import com.loja.ordercheckout.domain.port.in.RemoveFromCartUseCase;
import com.loja.ordercheckout.domain.port.in.UpdateCartLineUseCase;
import com.loja.ordercheckout.domain.port.out.CartRepositoryPort;
import com.loja.ordercheckout.domain.port.out.ProductLookupPort;
import com.loja.shared.domain.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application service implementing every cart use case.
 *
 * <p>Business rules that only depend on {@link Cart} live on the aggregate.
 * Rules that consult a port — product ACTIVE check, catalog snapshots,
 * persistence — live here.
 */
@ApplicationScoped
@Transactional
public class CartApplicationService implements AddToCartUseCase, UpdateCartLineUseCase,
        RemoveFromCartUseCase, GetCartUseCase, ClearCartUseCase, MergeGuestCartUseCase {

    private final CartRepositoryPort cartRepository;
    private final ProductLookupPort productLookup;

    @Inject
    public CartApplicationService(CartRepositoryPort cartRepository,
                                  ProductLookupPort productLookup) {
        this.cartRepository = cartRepository;
        this.productLookup = productLookup;
    }

    @Override
    public void add(String userId, String productId, int quantity) {
        requireNonBlank(userId, "userId");
        requireNonBlank(productId, "productId");
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        if (productLookup.findActiveById(productId).isEmpty()) {
            throw new CartProductNotAvailableException(productId);
        }
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> Cart.create(userId));
        cart.add(productId, quantity);
        cartRepository.save(cart);
    }

    @Override
    public void updateQuantity(String userId, String productId, int quantity) {
        requireNonBlank(userId, "userId");
        requireNonBlank(productId, "productId");
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartLineNotFoundException(productId));
        cart.updateQuantity(productId, quantity);
        if (cart.isEmpty()) {
            cartRepository.deleteByUserId(userId);
        } else {
            cartRepository.save(cart);
        }
    }

    @Override
    public void remove(String userId, String productId) {
        requireNonBlank(userId, "userId");
        requireNonBlank(productId, "productId");
        Optional<Cart> cart = cartRepository.findByUserId(userId);
        if (cart.isEmpty()) {
            return;
        }
        cart.get().remove(productId);
        if (cart.get().isEmpty()) {
            cartRepository.deleteByUserId(userId);
        } else {
            cartRepository.save(cart.get());
        }
    }

    @Override
    public void clear(String userId) {
        requireNonBlank(userId, "userId");
        cartRepository.deleteByUserId(userId);
    }

    @Override
    public void merge(String guestId, String userId) {
        requireNonBlank(guestId, "guestId");
        requireNonBlank(userId, "userId");
        if (guestId.trim().equals(userId.trim())) {
            return;
        }
        Optional<Cart> guestCart = cartRepository.findByUserId(guestId);
        if (guestCart.isEmpty()) {
            return;
        }
        Cart userCart = cartRepository.findByUserId(userId).orElseGet(() -> Cart.create(userId));
        userCart.merge(guestCart.get());
        cartRepository.save(userCart);
        cartRepository.deleteByUserId(guestId);
    }

    @Override
    public CartView getCart(String userId) {
        requireNonBlank(userId, "userId");
        return cartRepository.findByUserId(userId)
                .filter(cart -> !cart.isEmpty())
                .map(this::toView)
                .orElseGet(() -> new CartView(userId.trim(), List.of(), Money.zero()));
    }

    private CartView toView(Cart cart) {
        List<CartLineView> lines = new ArrayList<>(cart.getLines().size());
        Money subtotal = Money.zero();
        for (CartLine line : cart.getLines()) {
            CartLineView view = toLineView(line);
            if (view.available()) {
                subtotal = subtotal.add(view.lineTotal());
            }
            lines.add(view);
        }
        return new CartView(cart.getUserId(), lines, subtotal);
    }

    private CartLineView toLineView(CartLine line) {
        Optional<ProductSnapshot> snapshot = productLookup.findActiveById(line.productId());
        if (snapshot.isPresent()) {
            ProductSnapshot product = snapshot.get();
            Money unitPrice = product.price();
            return new CartLineView(line.productId(), product.name(), product.slug(),
                    line.quantity(), unitPrice, unitPrice.multiply(line.quantity()),
                    product.imageUrl(), true);
        }
        // Product no longer ACTIVE / removed: still show the row so it can be
        // removed, with a fallback label and no price.
        return new CartLineView(line.productId(), "Unavailable product", null,
                line.quantity(), Money.zero(), Money.zero(), null, false);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
