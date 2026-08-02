package com.loja.ordercheckout.application.dto;

/** A product and the quantity to order. */
public record ItemCheckoutRequest(String productId, int quantity) { }
