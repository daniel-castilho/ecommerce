package com.loja.productreviews.domain.model;

/**
 * Lifecycle of a {@link Review}.
 *
 * <p>A review is created in {@link #PENDING} and moves to either
 * {@link #APPROVED} or {@link #REJECTED} via admin moderation.
 * {@link #HIDDEN} is a soft-delete set by the author on an already
 * approved review (S14 — optional).
 */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    HIDDEN
}
