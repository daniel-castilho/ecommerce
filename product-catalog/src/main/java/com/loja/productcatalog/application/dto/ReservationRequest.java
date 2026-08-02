package com.loja.productcatalog.application.dto;

/**
 * A product and the quantity to reserve for a checkout. Carried by
 * {@link com.loja.productcatalog.domain.port.out.InventoryReservationPort}.
 */
public record ReservationRequest(String productId, int quantity) { }
