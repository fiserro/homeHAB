package io.github.fiserro.homehab.module;

import io.github.fiserro.homehab.ReadOnlyItem;
import io.github.fiserro.options.Option;
import io.github.fiserro.options.Options;

/**
 * Common module with shared sensors and system values.
 * These items are used across multiple modules.
 *
 * <p>Uses self-referential type parameter to allow extending classes
 * to maintain their own type in Options operations.
 *
 * <p>Sensor aggregations and MQTT bindings are specified in HabState via @MqttItem.
 *
 * @param <T> the implementing type (self-referential)
 */
public interface CommonModule<T extends CommonModule<T>> extends Options<T> {

    @Option
    default int temperature() { return 20; }

    @Option
    default int pressure() { return 1000; }

    @ReadOnlyItem @Option
    default int tickSecond() { return 0; }
}
