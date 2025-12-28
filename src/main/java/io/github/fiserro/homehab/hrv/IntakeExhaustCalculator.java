package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.module.HrvModule;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates intake and exhaust power levels based on base power and intake/exhaust ratio.
 *
 * <p>The ratio controls pressure balance:
 * <ul>
 *   <li>ratio = 0: balanced (intake = exhaust = basePower)</li>
 *   <li>ratio > 0: positive pressure (intake higher, exhaust lower)</li>
 *   <li>ratio < 0: negative pressure (exhaust higher, intake lower)</li>
 * </ul>
 *
 * <p>Example: basePower=70%, ratio=3% → intake=73%, exhaust=67%
 *
 * @param <T> the module type (extends HrvModule)
 */
@Slf4j
public class IntakeExhaustCalculator<T extends HrvModule<T>> implements Calculator<T> {

  @Override
  public T calculate(T state) {
    int basePower = state.hrvOutputPower();
    int ratio = state.intakeExhaustRatio();

    int intakePower = adjustPower(basePower, ratio);
    int exhaustPower = adjustPower(basePower, -ratio);

    log.debug("Calculated intake={}, exhaust={} (base={}, ratio={})",
        intakePower, exhaustPower, basePower, ratio);

    return state
        .withValue("hrvOutputIntake", intakePower)
        .withValue("hrvOutputExhaust", exhaustPower);
  }

  private int adjustPower(int basePower, int delta) {
    return Math.max(0, Math.min(100, basePower + delta));
  }
}
