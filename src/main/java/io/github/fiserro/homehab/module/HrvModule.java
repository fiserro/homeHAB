package io.github.fiserro.homehab.module;

import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import io.github.fiserro.options.Option;
import io.github.fiserro.options.Options;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * HRV (Heat Recovery Ventilator) control module. Contains all items related to HRV control,
 * sensors, and configuration.
 *
 * <p>Uses self-referential type parameter to allow extending classes to maintain their own type in
 * Options operations.
 *
 * <p>Sensor aggregations and MQTT bindings are specified in HabState via @MqttItem.
 *
 * @param <T> the implementing type (self-referential)
 */
public interface HrvModule<T extends HrvModule<T>> extends Options<T> {

  int POWER_OFF = 0;

  /** Outside temperature from 1-Wire sensor (DS18B20) on GPIO27. */
  @ReadOnlyItem
  @Option
  default float outsideTemperature() {
    return 0;
  }

  // Control modes
  @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:manualMode")
  @Option
  default boolean manualMode() {
    return false;
  }

  @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:tempManualMode")
  @Option
  default boolean temporaryManualMode() {
    return false;
  }

  @Max(43200)
  @Min(3600)
  @InputItem
  @Option
  default int temporaryManualModeDurationSec() {
    return 8 * 60 * 60;
  }

  @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:boostMode")
  @Option
  default boolean temporaryBoostMode() {
    return false;
  }

  @Max(3600)
  @Min(300)
  @InputItem
  @Option
  default int temporaryBoostModeDurationSec() {
    return 10 * 60;
  }

  // Timing (read-only, system-managed)
  @ReadOnlyItem
  @Option
  default long temporaryManualModeOffTime() {
    return 0;
  }

  @ReadOnlyItem
  @Option
  default long temporaryBoostModeOffTime() {
    return 0;
  }

  // Thresholds
  @Min(40)
  @Max(80)
  @InputItem
  @Option
  default int humidityThreshold() {
    return 60;
  }

  @Min(400)
  @Max(800)
  @InputItem
  @Option
  default int co2ThresholdLow() {
    return 500;
  }

  @Min(600)
  @Max(1000)
  @InputItem
  @Option
  default int co2ThresholdMid() {
    return 700;
  }

  @Min(800)
  @Max(1500)
  @InputItem
  @Option
  default int co2ThresholdHigh() {
    return 900;
  }

  // Intake/Exhaust ratio control
  /**
   * Pressure balance control. 0 = balanced, negative = underpressure, positive = overpressure.
   * Range -10 to +10: percentage adjustment relative to base power. -10 = underpressure (reduce
   * exhaust by 10% of base power, intake unchanged) +10 = overpressure (increase intake by 10% of
   * base power, exhaust unchanged) Example: base=20%, ratio=+10 → intake=22%, exhaust=20%
   */
  @Min(-10)
  @Max(10)
  @InputItem
  @Option
  default int intakeExhaustRatio() {
    return 0;
  }

  // Power levels
  @Min(0)
  @Max(100)
  @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:manualPower")
  @Option
  default int manualPower() {
    return 50;
  }

  @Min(0)
  @Max(100)
  @InputItem
  @Option
  default int powerLow() {
    return 15;
  }

  @Min(0)
  @Max(100)
  @InputItem
  @Option
  default int powerMid() {
    return 50;
  }

  @Min(0)
  @Max(100)
  @InputItem
  @Option
  default int powerHigh() {
    return 95;
  }

  // Sensor values (aggregation specified in HabState via @MqttItem)
  @Option
  default int openWindows() {
    return 0;
  }

  @Option
  default int airHumidity() {
    return 0;
  }

  @Option
  default int co2() {
    return 500;
  }

  @Option
  default boolean smoke() {
    return false;
  }

  @Option
  default boolean gas() {
    return false;
  }

  /**
   * Source for GPIO 18 output value. Options: POWER, INTAKE, EXHAUST, TEST, OFF - POWER: uses
   * hrvOutputPower - INTAKE: uses hrvOutputIntake - EXHAUST: uses hrvOutputExhaust - TEST: uses
   * hrvOutputTest (linear, no calibration) - OFF: disables GPIO output (0%)
   */
  @InputItem
  @Option
  default GpioSource sourceGpio18() {
    return GpioSource.INTAKE;
  }

  /** Source for GPIO 19 output value. Options: POWER, INTAKE, EXHAUST, TEST, OFF */
  @InputItem
  @Option
  default GpioSource sourceGpio19() {
    return GpioSource.EXHAUST;
  }

  /**
   * Calibration table for GPIO 18. JSON format: {"0": 0.0, "10": 1.48, "20": 2.63, ...} Maps PWM
   * duty cycle (%) to measured output voltage (V). Used by HrvCalculator to convert target voltage
   * to PWM %. Not applied when source is TEST.
   */
  @InputItem
  @Option
  default String calibrationTableGpio18() {
    return "";
  }

  /**
   * Calibration table for GPIO 19. JSON format: {"0": 0.0, "10": 1.48, "20": 2.63, ...} Maps PWM
   * duty cycle (%) to measured output voltage (V). Used by HrvCalculator to convert target voltage
   * to PWM %. Not applied when source is TEST.
   */
  @InputItem
  @Option
  default String calibrationTableGpio19() {
    return "";
  }

  /**
   * Base output power before intake/exhaust ratio adjustment. Calculated based on modes,
   * thresholds, and sensor values.
   */
  @OutputItem
  @Option
  default int hrvOutputPower() {
    return 50;
  }

  T withHrvOutputPower(int power);

  /**
   * Output power for intake (fresh air) motor. Calculated from hrvOutputPower adjusted by
   * intakeExhaustRatio.
   */
  @OutputItem
  @Option
  default int hrvOutputIntake() {
    return 50;
  }

  T withHrvOutputIntake(int power);

  /**
   * Output power for exhaust (stale air) motor. Calculated from hrvOutputPower adjusted by
   * intakeExhaustRatio.
   */
  @OutputItem
  @Option
  default int hrvOutputExhaust() {
    return 50;
  }

  T withHrvOutputExhaust(int power);

  /**
   * Test output value for calibration. When sourceGpioXX="TEST", this value is used for PWM output
   * (linear, no calibration).
   */
  @OutputItem
  @Option
  default int hrvOutputTest() {
    return 0;
  }

  /**
   * Final PWM value for GPIO 18 (0-100%). Calculated from source value (power/intake/exhaust/test)
   * with calibration applied.
   */
  @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:pwmGpio18")
  @Option
  default int hrvOutputGpio18() {
    return 0;
  }

  T withHrvOutputGpio18(int power);

  /**
   * Final PWM value for GPIO 19 (0-100%). Calculated from source value (power/intake/exhaust/test)
   * with calibration applied.
   */
  @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:pwmGpio19")
  @Option
  default int hrvOutputGpio19() {
    return 0;
  }

  T withHrvOutputGpio19(int power);

  /**
   * Bypass valve control (GPIO 17).
   * OFF = valve closed, air flows through heat exchanger (default)
   * ON = valve open, air bypasses heat exchanger
   */
  @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:bypass")
  @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:gpio17")
  @Option
  default boolean bypass() {
    return false;
  }

  T withBypass(boolean bypass);

  /**
   * Determines if the output state of the current module has changed compared to the given module.
   * The comparison is performed across multiple output-related properties, including
   * hrvOutputPower, hrvOutputIntake, hrvOutputExhaust, hrvOutputGpio18, and hrvOutputGpio19.
   *
   * @param other the module to compare against
   * @return true if any of the output-related properties have different values between
   *         the two modules; false otherwise
   */
  default boolean hasOutputChanged(HrvModule<?> other) {
    return hrvOutputPower() != other.hrvOutputPower()
        || hrvOutputIntake() != other.hrvOutputIntake()
        || hrvOutputExhaust() != other.hrvOutputExhaust()
        || hrvOutputGpio18() != other.hrvOutputGpio18()
        || hrvOutputGpio19() != other.hrvOutputGpio19();
  }

  /**
   * Returns the PWM duty cycle (%) for the given GPIO source.
   *
   * @param source the GPIO source
   * @return the PWM duty cycle (%)
   */
  default int targetPWM(GpioSource source) {
    return switch (source) {
      case POWER -> hrvOutputPower();
      case INTAKE -> hrvOutputIntake();
      case EXHAUST -> hrvOutputExhaust();
      case TEST -> hrvOutputTest();
      case OFF -> POWER_OFF;
    };
  }

  enum GpioSource {
    POWER,
    INTAKE,
    EXHAUST,
    TEST,
    OFF
  }
}
