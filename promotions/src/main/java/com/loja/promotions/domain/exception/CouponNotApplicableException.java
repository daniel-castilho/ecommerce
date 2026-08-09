package com.loja.promotions.domain.exception;

/** Thrown when a coupon exists but cannot be used right now (inactive, outside window or exhausted). */
public class CouponNotApplicableException extends RuntimeException {

    public CouponNotApplicableException(String message) {
        super(message);
    }
}
