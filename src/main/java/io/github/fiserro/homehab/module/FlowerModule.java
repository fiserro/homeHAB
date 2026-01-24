package io.github.fiserro.homehab.module;

import io.github.fiserro.options.Option;
import io.github.fiserro.options.Options;

/**
 * Flower/plant monitoring module.
 * Contains items related to plant care and soil monitoring.
 *
 * <p>Uses self-referential type parameter to allow extending classes
 * to maintain their own type in Options operations.
 *
 * @param <T> the implementing type (self-referential)
 */
public interface FlowerModule<T extends FlowerModule<T>> extends Options<T> {

    @Option
    default int soilHumidity() { return 0; }
}
