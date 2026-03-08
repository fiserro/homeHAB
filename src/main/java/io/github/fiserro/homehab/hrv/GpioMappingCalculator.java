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
    int gpio12 = state.targetPWM(state.sourceGpio12());
    int gpio13 = state.targetPWM(state.sourceGpio13());

    log.debug("GPIO mapping: source12={} → {}, source13={} → {}",
        state.sourceGpio12(), gpio12, state.sourceGpio13(), gpio13);

    return state
        .withHrvOutputGpio12(gpio12)
        .withHrvOutputGpio13(gpio13);
  }
}
