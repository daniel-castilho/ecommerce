package com.loja.productcatalog.application.dto;

import com.loja.productcatalog.domain.model.Product;

/**
 * A catalog search hit: the domain {@link Product} plus an optional, pre-sanitized
 * highlight snippet produced by Postgres {@code ts_headline}. {@code snippet} is
 * {@code null} when no text query is active or no headline source is available, and
 * when non-null it contains <b>only</b> {@code <mark>} / {@code </mark>} markup — the
 * adapter escapes everything else — so it is safe to render with {@code escape="false"}.
 */
public record ProductSearchHit(Product product, String snippet) {
}