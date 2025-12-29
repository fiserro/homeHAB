package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.module.HrvModule;
import java.util.function.Function;

/**
 * Facade calculator that chains all HRV calculation steps.
 *
 * <p>This calculator delegates to:
 * <ol>
 *   <li>{@link BypassCalculator} - calculates automatic bypass based on temperatures</li>
 *   <li>{@link PowerCalculator} - calculates base power from conditions</li>
 *   <li>{@link IntakeExhaustCalculator} - calculates intake/exhaust based on ratio</li>
 *   <li>{@link GpioMappingCalculator} - maps source selection to GPIO values</li>
 *   <li>{@link CalibrationCalculator} - applies GPIO calibration</li>
 * </ol>
 *
 * @param <T> the module type (extends HrvModule)
 */
public class HrvCalculator<T extends HrvModule<T>> implements Calculator<T> {

  private final Function<T, T> chain = new BypassCalculator<T>()
      .andThen(new PowerCalculator<>())
      .andThen(new IntakeExhaustCalculator<>())
      .andThen(new GpioMappingCalculator<>())
      .andThen(new CalibrationCalculator<>());

  @Override
  public T calculate(T state) {
    return chain.apply(state);
  }
}
