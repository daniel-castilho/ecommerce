package com.loja.useraccount.domain.model;

/**
 * Roles supported by the user-account module.
 */
public enum Role {
    CUSTOMER("customer", "Can browse catalog, checkout, view orders"),
    ADMIN("admin", "Full system access"),
    VENDOR("vendor", "Can manage own products");

    private final String code;
    private final String description;

    Role(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
