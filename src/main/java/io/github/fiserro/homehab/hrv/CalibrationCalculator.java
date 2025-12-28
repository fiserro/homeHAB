package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.module.HrvModule;
import io.github.fiserro.homehab.module.HrvModule.GpioSource;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies calibration to GPIO PWM values.
 *
 * <p>Uses linear interpolation between calibration points to convert target percentage
 * to actual PWM duty cycle values. Skips calibration for TEST and OFF sources.
 *
 * @param <T> the module type (extends HrvModule)
 */
@Slf4j
public class CalibrationCalculator<T extends HrvModule<T>> implements Calculator<T> {

  @Override
  public T calculate(T state) {
    int gpio18 = calibrate(state.hrvOutputGpio18(), state.calibrationTableGpio18(), state.sourceGpio18());
    int gpio19 = calibrate(state.hrvOutputGpio19(), state.calibrationTableGpio19(), state.sourceGpio19());

    log.debug("Calibration applied: gpio18={}, gpio19={}", gpio18, gpio19);

    return state
        .withHrvOutputGpio18(gpio18)
        .withHrvOutputGpio19(gpio19);
  }

  private int calibrate(int value, String calibrationTable, GpioSource source) {
    if (source == GpioSource.TEST || source == GpioSource.OFF) {
      return value;
    }
    return applyCalibration(value, calibrationTable);
  }

  /**
   * Apply calibration table to convert target percentage to PWM duty cycle.
   * Uses linear interpolation between calibration points.
   *
   * @param targetPercent desired output (0-100%)
   * @param calibrationTable calibration table in format "pwm:voltage,pwm:voltage,..."
   * @return calibrated PWM duty cycle (0-100)
   */
  int applyCalibration(int targetPercent, String calibrationTable) {
    Map<Integer, Double> calibration = parseCalibration(calibrationTable);

    if (calibration.isEmpty() || calibration.size() < 2) {
      return targetPercent;
    }

    // Target voltage = targetPercent / 100 * 10V
    double targetVoltage = targetPercent / 100.0 * 10.0;

    return (int) Math.round(pwmForVoltage(targetVoltage, calibration));
  }

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
}
