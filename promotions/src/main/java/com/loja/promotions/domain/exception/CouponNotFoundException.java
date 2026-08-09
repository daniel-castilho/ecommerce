package com.loja.promotions.domain.exception;

/** Thrown when a coupon code is unknown or blank. */
public class CouponNotFoundException extends RuntimeException {

    public CouponNotFoundException(String message) {
        super(message);
    }
}
