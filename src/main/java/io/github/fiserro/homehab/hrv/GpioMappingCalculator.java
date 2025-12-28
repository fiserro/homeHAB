package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.module.HrvModule;
import io.github.fiserro.homehab.module.HrvModule.GpioSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps source selection to GPIO output values.
 *
 * <p>For each GPIO pin, reads the source setting (POWER, INTAKE, EXHAUST, TEST, OFF)
 * and sets the corresponding output value:
 * <ul>
 *   <li>POWER → hrvOutputPower</li>
 *   <li>INTAKE → hrvOutputIntake</li>
 *   <li>EXHAUST → hrvOutputExhaust</li>
 *   <li>TEST → hrvOutputTest</li>
 *   <li>OFF → 0</li>
 * </ul>
 *
 * @param <T> the module type (extends HrvModule)
 */
@Slf4j
public class GpioMappingCalculator<T extends HrvModule<T>> implements Calculator<T> {

  @Override
  public T calculate(T state) {
    int gpio18 = state.targetPWM(state.sourceGpio18());
    int gpio19 = state.targetPWM(state.sourceGpio19());

    log.debug("GPIO mapping: source18={} → {}, source19={} → {}",
        state.sourceGpio18(), gpio18, state.sourceGpio19(), gpio19);

    return state
        .withHrvOutputGpio18(gpio18)
        .withHrvOutputGpio19(gpio19);
  }
}
