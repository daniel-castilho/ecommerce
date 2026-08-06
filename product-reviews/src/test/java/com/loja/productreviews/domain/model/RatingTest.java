package com.loja.productreviews.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.loja.productreviews.domain.exception.InvalidRatingException;

class RatingTest {

    @Test
    void shouldAcceptEveryValidStarValue() {
        for (int stars = 1; stars <= 5; stars++) {
            Rating rating = Rating.of(stars);
            assertThat(rating.getValue()).isEqualTo(stars);
            assertThat(rating).isEqualTo(Rating.of(stars));
            assertThat(rating.hashCode()).isEqualTo(Rating.of(stars).hashCode());
        }
    }

    @Test
    void shouldRejectZero() {
        assertThatThrownBy(() -> Rating.of(0))
                .isInstanceOf(InvalidRatingException.class)
                .hasMessageContaining("0");
    }

    @Test
    void shouldRejectSix() {
        assertThatThrownBy(() -> Rating.of(6))
                .isInstanceOf(InvalidRatingException.class)
                .hasMessageContaining("6");
    }

    @Test
    void shouldRejectNegative() {
        assertThatThrownBy(() -> Rating.of(-1))
                .isInstanceOf(InvalidRatingException.class);
    }
}
