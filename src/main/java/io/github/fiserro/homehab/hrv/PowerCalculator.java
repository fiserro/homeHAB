package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.module.HrvModule;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates HRV base power level based on current conditions.
 *
 * <p>Priority-based decision tree:
 * <ol>
 *   <li>Manual modes (manual, temporary manual) → manualPower</li>
 *   <li>Boost mode (temporary boost) → powerHigh</li>
 *   <li>Gas detection → powerHigh</li>
 *   <li>Smoke detection → POWER_OFF</li>
 *   <li>Humidity threshold → powerHigh</li>
 *   <li>CO2 levels → powerHigh/Mid/Low based on thresholds</li>
 *   <li>Default: powerLow</li>
 * </ol>
 *
 * @param <T> the module type (extends HrvModule)
 */
@Slf4j
public class PowerCalculator<T extends HrvModule<T>> implements Calculator<T> {

  @Override
  public T calculate(T state) {
    int power = calculatePower(state);
    log.debug("Calculated base power: {}", power);
    return state.withHrvOutputPower(power);
  }

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
