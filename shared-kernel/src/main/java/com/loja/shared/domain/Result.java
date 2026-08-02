package com.loja.shared.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public sealed interface Result<V, E> permits Result.Success, Result.Failure {

    record Success<V, E>(V value) implements Result<V, E> {}
    record Failure<V, E>(E error) implements Result<V, E> {}

    static <V, E> Result<V, E> success(V value) {
        return new Success<>(value);
    }

    static <V, E> Result<V, E> failure(E error) {
        return new Failure<>(error);
    }

    default boolean isSuccess() { return this instanceof Success; }
    default boolean isFailure() { return this instanceof Failure; }

    default Optional<V> getValue() {
        return this instanceof Success<V, E> s ? Optional.of(s.value) : Optional.empty();
    }

    default Optional<E> getError() {
        return this instanceof Failure<V, E> f ? Optional.of(f.error) : Optional.empty();
    }

    default V orElseThrow() {
        if (this instanceof Success<V, E> s) return s.value;
        throw new IllegalStateException("Result was Failure: " + ((Failure<V, E>) this).error);
    }

    default <T> Result<T, E> map(Function<? super V, ? extends T> mapper) {
        return switch (this) {
            case Success<V, E> s -> Result.success(mapper.apply(s.value));
            case Failure<V, E> f -> Result.failure(f.error);
        };
    }

    default <T> Result<T, E> flatMap(Function<? super V, Result<T, E>> mapper) {
        return switch (this) {
            case Success<V, E> s -> mapper.apply(s.value);
            case Failure<V, E> f -> Result.failure(f.error);
        };
    }

    default void ifSuccess(Consumer<? super V> action) {
        if (this instanceof Success<V, E> s) action.accept(s.value);
    }

    default void ifFailure(Consumer<? super E> action) {
        if (this instanceof Failure<V, E> f) action.accept(f.error);
    }
}
