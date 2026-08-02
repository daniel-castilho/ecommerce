package com.loja.productcatalog.domain.model;

import java.util.ArrayList;
import java.util.List;

public class Category {

    private static final int NAME_MAX_LENGTH = 255;

    private final Long id;
    private String name;
    private final Slug slug;
    private Category parent;
    private int position;
    private boolean active;
    private Long version;
    private final List<Category> children;

    public Category(Long id, String name, Slug slug, Category parent, int position, boolean active) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Category name exceeds " + NAME_MAX_LENGTH + " characters");
        }
        if (slug == null) {
            throw new IllegalArgumentException("Category slug is required");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Category position cannot be negative");
        }
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.parent = parent;
        this.position = position;
        this.active = active;
        this.children = new ArrayList<>();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Slug getSlug() { return slug; }
    public Category getParent() { return parent; }
    public int getPosition() { return position; }
    public boolean isActive() { return active; }
    public List<Category> getChildren() { return children; }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        this.name = name;
    }

    public void setParent(Category parent) { this.parent = parent; }

    public void setPosition(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("Category position cannot be negative");
        }
        this.position = position;
    }

    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public void addChild(Category child) { this.children.add(child); }
}
