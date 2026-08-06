package com.loja.productreviews.domain.model;

/**
 * Customer-provided star rating for a product. Value object.
 *
 * <p>Immutable wrapper around an integer in the closed range [1, 5].
 * Construction rejects null and out-of-range values with
 * {@link com.loja.productreviews.domain.exception.InvalidRatingException}.
 */
public final class Rating {

    private final int value;

    private Rating(int value) {
        this.value = value;
    }

    /**
     * Build a rating from a primitive int.
     *
     * @param value stars, must be between 1 and 5 inclusive
     * @return a new {@link Rating}
     * @throws com.loja.productreviews.domain.exception.InvalidRatingException if out of range
     */
    public static Rating of(int value) {
        if (value < 1 || value > 5) {
            throw new com.loja.productreviews.domain.exception.InvalidRatingException(
                    "Rating must be between 1 and 5, got: " + value);
        }
        return new Rating(value);
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rating rating)) return false;
        return value == rating.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
