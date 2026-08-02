package com.loja.productcatalog.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public final class Slug {

    private static final int MAX_LENGTH = 160;
    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final String value;

    public Slug(String value) {
        if (value == null || !FORMAT.matcher(value).matches() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid slug: " + value);
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Slug slug)) return false;
        return value.equals(slug.value);
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
