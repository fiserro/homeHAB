package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.module.HrvModule;
import io.github.fiserro.homehab.module.HrvModule.GpioSource;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculation logic for the HRV system. Determines ventilator power based on inputs and
 * configuration.
 *
 * <p>This calculator works with any implementation of {@link HrvModule}, making it reusable across
 * different home automation setups.
 *
 * @param <T> the module type (extends HrvModule)
 */
@Slf4j
public class HrvCalculator<T extends HrvModule<T>> implements Calculator<T> {

  @Override
  public T calculate(T state) {

    int basePower = calculatePower(state);
    int ratio = state.intakeExhaustRatio();
    int intakePower = adjustPower(basePower, ratio);
    int exhaustPower = adjustPower(basePower, -ratio);

    // Calculate intake and exhaust power based on ratio (pressure control)
    // ratio = 0: balanced (intake = exhaust = basePower)
    // ratio != 0: apply correction to both sides (percentage of max power 100%)
    // Example: basePower=70%, ratio=3% → intake=73%, exhaust=67%
    state = state
        .withValue("hrvOutputPower", basePower)
        .withValue("hrvOutputIntake", intakePower)
        .withValue("hrvOutputExhaust", exhaustPower);

    log.debug(
        "Calculated HRV power levels: base={}, intake={}, exhaust={}",
        basePower, intakePower, exhaustPower);

    // Calculate final GPIO PWM values based on source mapping and calibration table
    int gpio18Pwm =
        calculateGpioPwm(
            state.sourceGpio18(),
            state.targetPWM(state.sourceGpio18()),
            state.calibrationTableGpio18());
    int gpio19Pwm =
        calculateGpioPwm(
            state.sourceGpio19(),
            state.targetPWM(state.sourceGpio19()),
            state.calibrationTableGpio19());

    log.debug("Calculated GPIO PWM values: gpio18={}, gpio19={}", gpio18Pwm, gpio19Pwm);

    return state
        .withValue("hrvOutputGpio18", gpio18Pwm)
        .withValue("hrvOutputGpio19", gpio19Pwm);
  }

  /**
   * Calculate PWM value for a GPIO based on source and calibration.
   *
   * @param source the source selection (POWER, INTAKE, EXHAUST, TEST, OFF)
   * @param calibration JSON calibration table
   * @return PWM duty cycle (0-100)
   */
  int calculateGpioPwm(GpioSource source, int targetPwm, String calibration) {
    return switch (source) {
      case TEST, OFF -> targetPwm;
      default -> applyCalibration(targetPwm, calibration);
    };
  }

  /**
   * Apply calibration table to convert target percentage to PWM duty cycle. Uses linear
   * interpolation between calibration points.
   *
   * @param targetPercent desired output (0-100%)
   * @param calibrationTable calibration table in format "pwm:voltage,pwm:voltage,..."
   * @return calibrated PWM duty cycle (0-100)
   */
  int applyCalibration(int targetPercent, String calibrationTable) {
    Map<Integer, Double> calibration = parseCalibration(calibrationTable);

    if (calibration.isEmpty() || calibration.size() < 2) {
      // No valid calibration, use linear (1:1 mapping)
      return targetPercent;
    }

    // Target voltage = targetPercent / 100 * 10V
    double targetVoltage = targetPercent / 100.0 * 10.0;

    return (int) Math.round(pwmForVoltage(targetVoltage, calibration));
  }

  /** Find PWM duty cycle for target voltage using linear interpolation. */
  private double pwmForVoltage(double targetVoltage, Map<Integer, Double> calibration) {
    TreeMap<Integer, Double> sorted = new TreeMap<>(calibration);

    // Clamp target to calibration voltage range
    double minV = Collections.min(sorted.values());
    double maxV = Collections.max(sorted.values());
    targetVoltage = Math.max(minV, Math.min(maxV, targetVoltage));

    // Find bracketing points
    Integer lowerPwm = null;
    Double lowerV = null;
    Integer upperPwm = null;
    Double upperV = null;

    for (Map.Entry<Integer, Double> entry : sorted.entrySet()) {
      if (entry.getValue() <= targetVoltage) {
        lowerPwm = entry.getKey();
        lowerV = entry.getValue();
      }
      if (entry.getValue() >= targetVoltage && upperPwm == null) {
        upperPwm = entry.getKey();
        upperV = entry.getValue();
      }
    }

    // Edge cases
    if (lowerPwm == null) {
      return sorted.firstKey();
    }
    if (upperPwm == null) {
      return sorted.lastKey();
    }
    if (Math.abs(upperV - lowerV) < 0.001) {
      return lowerPwm;
    }

    // Linear interpolation
    double ratio = (targetVoltage - lowerV) / (upperV - lowerV);
    return lowerPwm + ratio * (upperPwm - lowerPwm);
  }

  /**
   * Parse calibration table in format "pwm:voltage,pwm:voltage,...".
   * Example: "0:0,50:5.5,100:10"
   */
  private Map<Integer, Double> parseCalibration(String calibrationTable) {
    if (calibrationTable == null || calibrationTable.isBlank()) {
      return Collections.emptyMap();
    }

    try {
      Map<Integer, Double> result = new TreeMap<>();
      for (String pair : calibrationTable.split(",")) {
        String[] parts = pair.split(":");
        if (parts.length == 2) {
          int pwm = Integer.parseInt(parts[0].trim());
          double voltage = Double.parseDouble(parts[1].trim());
          if (pwm >= 0 && pwm <= 100 && voltage >= 0 && voltage <= 20) {
            result.put(pwm, voltage);
          }
        }
      }
      return result;
    } catch (Exception e) {
      log.warn("Failed to parse calibration table '{}': {}", calibrationTable, e.getMessage());
      return Collections.emptyMap();
    }
  }

  /** Adjust power level by the given delta, clamping to valid range 0-100. */
  private int adjustPower(int basePower, int delta) {
    return Math.max(0, Math.min(100, basePower + delta));
  }

  /**
   * Calculate the HRV power level based on current state.
   *
   * @param state the HRV module state
   * @return calculated power level (0-100)
   */
  private int calculatePower(HrvModule<?> state) {
    if (state.manualMode() || state.temporaryManualMode()) {
      return state.manualPower();
    }

    if (state.temporaryBoostMode()) {
      return state.powerHigh();
    }

    if (state.gas()) {
      return state.powerHigh();
    }

    if (state.smoke()) {
      return HrvModule.POWER_OFF;
    }

    if (state.airHumidity() >= state.humidityThreshold()) {
      return state.powerHigh();
    }

    if (state.co2() >= state.co2ThresholdHigh()) {
      return state.powerHigh();
    }

    if (state.co2() >= state.co2ThresholdMid()) {
      return state.powerMid();
    }

    if (state.co2() >= state.co2ThresholdLow()) {
      return state.powerLow();
    }

    return state.powerLow();
  }
}
