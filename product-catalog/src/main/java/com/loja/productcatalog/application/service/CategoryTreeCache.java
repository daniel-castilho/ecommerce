package com.loja.productcatalog.application.service;

import com.loja.productcatalog.domain.model.Category;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.function.Supplier;

/**
 * Application-scoped in-memory cache for the full category tree. The tree changes
 * infrequently and is read on every catalog page load, so it is cached and invalidated
 * on any category create/update/delete (done by the persistence adapter on mutation).
 */
@ApplicationScoped
public class CategoryTreeCache {

    private volatile List<Category> tree;

    public List<Category> getOrLoad(Supplier<List<Category>> loader) {
        List<Category> current = tree;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (tree == null) {
                tree = List.copyOf(loader.get());
            }
            return tree;
        }
    }

    public void invalidate() {
        tree = null;
    }
}
