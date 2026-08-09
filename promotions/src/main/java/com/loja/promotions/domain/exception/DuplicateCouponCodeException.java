package com.loja.promotions.domain.exception;

/** Thrown when trying to create a coupon with a code that already exists. */
public class DuplicateCouponCodeException extends RuntimeException {

    public DuplicateCouponCodeException(String message) {
        super(message);
    }
}
