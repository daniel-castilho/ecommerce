package com.loja.productcatalog.application.service;

import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Slug;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTreeCacheTest {

    @Test
    void loadsOnceUntilInvalidated() {
        CategoryTreeCache cache = new CategoryTreeCache();
        AtomicInteger loads = new AtomicInteger();

        List<Category> first = cache.getOrLoad(() -> {
            loads.incrementAndGet();
            return List.of(category());
        });
        assertThat(first).hasSize(1);

        List<Category> second = cache.getOrLoad(() -> {
            loads.incrementAndGet();
            return List.of(category());
        });
        assertThat(second).hasSize(1);
        assertThat(loads).hasValue(1);

        cache.invalidate();
        cache.getOrLoad(() -> {
            loads.incrementAndGet();
            return List.of(category());
        });
        assertThat(loads).hasValue(2);
    }

    private Category category() {
        return new Category(null, "Eletronicos", new Slug("eletronicos"), null, 0, true);
    }
}
