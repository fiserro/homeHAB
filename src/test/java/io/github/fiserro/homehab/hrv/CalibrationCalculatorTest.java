package io.github.fiserro.homehab.hrv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.fiserro.homehab.TestHabState;
import io.github.fiserro.homehab.module.HrvModule.GpioSource;
import io.github.fiserro.options.OptionsFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Parameterized tests for {@link CalibrationCalculator}.
 *
 * <p>Tests the calibration logic that converts target percentage to actual PWM duty cycle
 * using linear interpolation between calibration points. Skips calibration for TEST/OFF sources.
 */
class CalibrationCalculatorTest {

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(resources = "/calibration-calculator-scenarios.csv", numLinesToSkip = 1,
      delimiter = ';', nullValues = {"-"})
  @DisplayName("CalibrationCalculator scenario")
  void testScenario(
      String scenario,
      int hrvOutputGpio12,
      int hrvOutputGpio13,
      GpioSource sourceGpio12,
      GpioSource sourceGpio13,
      String calibrationTableGpio12,
      String calibrationTableGpio13,
      int expectedGpio12,
      int expectedGpio13) {

    TestHabState state = OptionsFactory.create(TestHabState.class)
        .withValue("hrvOutputGpio12", hrvOutputGpio12)
        .withValue("hrvOutputGpio13", hrvOutputGpio13)
        .withValue("sourceGpio12", sourceGpio12)
        .withValue("sourceGpio13", sourceGpio13)
        .withValue("calibrationTableGpio12", calibrationTableGpio12 != null ? calibrationTableGpio12 : "")
        .withValue("calibrationTableGpio13", calibrationTableGpio13 != null ? calibrationTableGpio13 : "");

    CalibrationCalculator<TestHabState> calculator = new CalibrationCalculator<>();
    TestHabState result = calculator.calculate(state);

    assertEquals(expectedGpio12, result.hrvOutputGpio12(),
        "hrvOutputGpio12 mismatch for scenario: " + scenario);
    assertEquals(expectedGpio13, result.hrvOutputGpio13(),
        "hrvOutputGpio13 mismatch for scenario: " + scenario);
  }
}
