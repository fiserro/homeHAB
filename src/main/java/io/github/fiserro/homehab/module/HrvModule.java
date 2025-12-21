package io.github.fiserro.homehab.module;

import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import io.github.fiserro.options.Option;
import io.github.fiserro.options.Options;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * HRV (Heat Recovery Ventilator) control module.
 * Contains all items related to HRV control, sensors, and configuration.
 *
 * <p>Uses self-referential type parameter to allow extending classes
 * to maintain their own type in Options operations.
 *
 * <p>Sensor aggregations and MQTT bindings are specified in HabState via @MqttItem.
 *
 * @param <T> the implementing type (self-referential)
 */
public interface HrvModule<T extends HrvModule<T>> extends Options<T> {

    int POWER_OFF = 0;

    // Control modes
    @InputItem @Option
    default boolean manualMode() { return false; }

    @InputItem @Option
    default boolean temporaryManualMode() { return false; }

    @Max(43200) @Min(3600) @InputItem @Option
    default int temporaryManualModeDurationSec() { return 8 * 60 * 60; }

    @InputItem @Option
    default boolean temporaryBoostMode() { return false; }

    @Max(3600) @Min(300) @InputItem @Option
    default int temporaryBoostModeDurationSec() { return 10 * 60; }

    // Timing (read-only, system-managed)
    @ReadOnlyItem @Option
    default long temporaryManualModeOffTime() { return 0; }

    @ReadOnlyItem @Option
    default long temporaryBoostModeOffTime() { return 0; }

    // Thresholds
    @Min(40) @Max(80) @InputItem @Option
    default int humidityThreshold() { return 60; }

    @Min(400) @Max(800) @InputItem @Option
    default int co2ThresholdLow() { return 500; }

    @Min(600) @Max(1000) @InputItem @Option
    default int co2ThresholdMid() { return 700; }

    @Min(800) @Max(1500) @InputItem @Option
    default int co2ThresholdHigh() { return 900; }

    // Power levels
    @Min(0) @Max(100) @InputItem @Option
    default int manualPower() { return 50; }

    @Min(0) @Max(100) @InputItem @Option
    default int powerLow() { return 15; }

    @Min(0) @Max(100) @InputItem @Option
    default int powerMid() { return 50; }

    @Min(0) @Max(100) @InputItem @Option
    default int powerHigh() { return 95; }

    // Sensor values (aggregation specified in HabState via @MqttItem)
    @Option
    default int openWindows() { return 0; }

    @Option
    default int airHumidity() { return 0; }

    @Option
    default int co2() { return 500; }

    @Option
    default boolean smoke() { return false; }

    @Option
    default boolean gas() { return false; }

    // Output
    @OutputItem @Option
    default int hrvOutputPower() { return 50; }

    T withHrvOutputPower(int power);

}
