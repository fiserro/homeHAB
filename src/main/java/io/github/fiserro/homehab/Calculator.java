package io.github.fiserro.homehab;

import io.github.fiserro.homehab.module.HrvModule;
import java.util.function.Function;

/**
 * Generic calculator interface for HRV module.
 * Extends Function to allow use in functional pipelines.
 *
 * <p>The type T extends HrvModule which already includes Options<T>.
 *
 * @param <T> the module type (extends HrvModule)
 */
public interface Calculator<T extends HrvModule<T>> extends Function<T, T> {

    T calculate(T state);

    @Override
    default T apply(T state) {
        return calculate(state);
    }
}
