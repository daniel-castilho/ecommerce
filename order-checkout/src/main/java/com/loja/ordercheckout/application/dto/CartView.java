package com.loja.ordercheckout.application.dto;

import com.loja.shared.domain.Money;
import java.util.List;

/**
 * Public-facing representation of the customer's cart: the lines enriched with
 * live catalog snapshots plus the merchandise subtotal (unavailable lines
 * contribute zero).
 */
public record CartView(String userId, List<CartLineView> lines, Money subtotal) {
}
