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

    @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:tempManualMode") @Option
    default boolean temporaryManualMode() { return false; }

    @Max(43200) @Min(3600) @InputItem @Option
    default int temporaryManualModeDurationSec() { return 8 * 60 * 60; }

    @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:boostMode") @Option
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

    // Motor control mode
    /**
     * Dual motor mode flag:
     * - false (default): Single motor mode - uses hrvOutputPower for both motors
     * - true: Dual motor mode - uses hrvOutputIntake (GPIO 18) and hrvOutputExhaust (GPIO 19)
     * When switching modes, manually update channel links in OpenHAB UI.
     */
    @InputItem @Option
    default boolean dualMotorMode() { return false; }

    // Intake/Exhaust ratio control
    /**
     * Pressure balance control. 0 = balanced, negative = underpressure, positive = overpressure.
     * Range -10 to +10: percentage adjustment relative to base power.
     * -10 = underpressure (reduce exhaust by 10% of base power, intake unchanged)
     * +10 = overpressure (increase intake by 10% of base power, exhaust unchanged)
     * Example: base=20%, ratio=+10 → intake=22%, exhaust=20%
     * Only effective when dualMotorMode=true.
     */
    @Min(-10) @Max(10) @InputItem @Option
    default int intakeExhaustRatio() { return 0; }

    // Power levels
    @Min(0) @Max(100) @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:manualPower") @Option
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
    // Channel links for HRV Bridge (see hrv-bridge.things):
    // - Single motor mode: use hrvOutputPower → power channel (controls both motors equally)
    // - Dual motor mode: use hrvOutputIntake/Exhaust → intake/exhaust channels (independent control)
    @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:power") @Option
    default int hrvOutputPower() { return 50; }

    /**
     * Output power for intake (fresh air) motor.
     * Calculated from hrvOutputPower adjusted by intakeExhaustRatio.
     */
    @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:intake") @Option
    default int hrvOutputIntake() { return 50; }

    /**
     * Output power for exhaust (stale air) motor.
     * Calculated from hrvOutputPower adjusted by intakeExhaustRatio.
     */
    @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:exhaust") @Option
    default int hrvOutputExhaust() { return 50; }

    T withHrvOutputPower(int power);
    T withHrvOutputIntake(int power);
    T withHrvOutputExhaust(int power);

    // ========== GPIO Configuration ==========
    /**
     * Source for GPIO 18 output value.
     * Options: "power", "intake", "exhaust", "test", "off"
     * - power: uses hrvOutputPower
     * - intake: uses hrvOutputIntake
     * - exhaust: uses hrvOutputExhaust
     * - test: uses hrvOutputTest (for calibration)
     * - off: disables GPIO output
     */
    @InputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:gpio18Source") @Option
    default GpioSource gpio18Source() { return GpioSource.INTAKE; }

    /**
     * Source for GPIO 19 output value.
     * Options: "power", "intake", "exhaust", "test", "off"
     * - off: disables GPIO output
     */
    @InputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:gpio19Source") @Option
    default GpioSource gpio19Source() { return GpioSource.EXHAUST; }

    enum GpioSource {
        POWER,
        INTAKE,
        EXHAUST,
        TEST,
        OFF
    }

    /**
     * Test output value for calibration.
     * When gpioXXSource="test", this value is used for PWM output.
     * Allows testing specific PWM values without affecting normal operation.
     */
    @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:test") @Option
    default int hrvOutputTest() { return 0; }

    // ========== Calibration Tables ==========

    /**
     * Calibration table for GPIO 18.
     * JSON format: {"0": 0.0, "10": 1.48, "20": 2.63, ...}
     * Maps PWM duty cycle (%) to measured output voltage (V).
     * Set to {"0":0, "100":10} for linear (uncalibrated) mode.
     */
    @InputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:calibrationGpio18") @Option
    default String calibrationTableGpio18() { return "{}"; }

    /**
     * Calibration table for GPIO 19.
     * JSON format: {"0": 0.0, "10": 1.48, "20": 2.63, ...}
     * Maps PWM duty cycle (%) to measured output voltage (V).
     * Set to {"0":0, "100":10} for linear (uncalibrated) mode.
     */
    @InputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:calibrationGpio19") @Option
    default String calibrationTableGpio19() { return "{}"; }

}
