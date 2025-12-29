package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.module.HrvModule;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates automatic bypass valve state based on temperature conditions.
 *
 * <p>Bypass is used to let fresh air in without going through the heat exchanger,
 * useful when inside temperature is higher than preferred and outside is cooler.
 *
 * <p>Logic:
 * <ul>
 *   <li>Skip if manual mode or temporary manual mode is active</li>
 *   <li>Bypass ON when: insideTemp > preferredTemp + hysteresis/2 AND insideTemp > outsideTemp + hysteresis/2</li>
 *   <li>Bypass OFF when: insideTemp < preferredTemp - hysteresis/2 OR insideTemp < outsideTemp - hysteresis/2</li>
 *   <li>Keep current state if in hysteresis zone</li>
 * </ul>
 *
 * @param <T> the module type (extends HrvModule)
 */
@Slf4j
public class BypassCalculator<T extends HrvModule<T>> implements Calculator<T> {

  @Override
  public T calculate(T state) {
    // Skip automatic bypass control in manual modes
    if (state.manualMode() || state.temporaryManualMode()) {
      log.debug("Bypass: manual mode active, skipping automatic control");
      return state;
    }

    float insideTemp = state.insideTemperature();
    float outsideTemp = state.outsideTemperature();
    float preferredTemp = state.preferredTemperature();
    float hysteresis = state.bypassHysteresis();
    float halfHysteresis = hysteresis / 2;
    boolean currentBypass = state.bypass();

    // Calculate thresholds
    float preferredUpperThreshold = preferredTemp + halfHysteresis;
    float preferredLowerThreshold = preferredTemp - halfHysteresis;
    float outsideUpperThreshold = outsideTemp + halfHysteresis;
    float outsideLowerThreshold = outsideTemp - halfHysteresis;

    boolean shouldTurnOn = insideTemp > preferredUpperThreshold && insideTemp > outsideUpperThreshold;
    boolean shouldTurnOff = insideTemp < preferredLowerThreshold || insideTemp < outsideLowerThreshold;

    boolean newBypass;
    if (shouldTurnOn) {
      newBypass = true;
      log.debug("Bypass ON: inside={}, preferred={}, outside={}", insideTemp, preferredTemp, outsideTemp);
    } else if (shouldTurnOff) {
      newBypass = false;
      log.debug("Bypass OFF: inside={}, preferred={}, outside={}", insideTemp, preferredTemp, outsideTemp);
    } else {
      // In hysteresis zone - keep current state
      newBypass = currentBypass;
      log.debug("Bypass unchanged (hysteresis): inside={}, preferred={}, outside={}, current={}",
          insideTemp, preferredTemp, outsideTemp, currentBypass);
    }

    return state.withBypass(newBypass);
  }
}
