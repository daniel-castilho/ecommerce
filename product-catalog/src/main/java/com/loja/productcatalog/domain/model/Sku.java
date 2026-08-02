package com.loja.productcatalog.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class Sku {

    private static final int MAX_LENGTH = 64;
    private static final Pattern FORMAT = Pattern.compile("^[A-Z0-9]+(-[A-Z0-9]+)*$");

    private final String value;

    public Sku(String value) {
        if (value == null) {
            throw new IllegalArgumentException("SKU is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH || !FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid SKU: " + value);
        }
        this.value = normalized;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sku sku)) return false;
        return value.equals(sku.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
