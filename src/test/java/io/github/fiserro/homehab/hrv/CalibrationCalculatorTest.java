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
 * <p>Tests GPIO PWM calculation based on source selection and calibration tables.
 * Uses linear interpolation between calibration points to convert target percentage
 * to actual PWM duty cycle values.
 */
class CalibrationCalculatorTest {

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(resources = "/calibration-calculator-scenarios.csv", numLinesToSkip = 1,
      delimiter = ';', nullValues = {"-"})
  @DisplayName("CalibrationCalculator scenario")
  void testScenario(
      String scenario,
      GpioSource sourceGpio18,
      GpioSource sourceGpio19,
      int hrvOutputPower,
      int hrvOutputIntake,
      int hrvOutputExhaust,
      int hrvOutputTest,
      String calibrationTableGpio18,
      String calibrationTableGpio19,
      int expectedGpio18,
      int expectedGpio19) {

    TestHabState state = OptionsFactory.create(TestHabState.class)
        .withValue("sourceGpio18", sourceGpio18)
        .withValue("sourceGpio19", sourceGpio19)
        .withValue("hrvOutputPower", hrvOutputPower)
        .withValue("hrvOutputIntake", hrvOutputIntake)
        .withValue("hrvOutputExhaust", hrvOutputExhaust)
        .withValue("hrvOutputTest", hrvOutputTest)
        .withValue("calibrationTableGpio18", calibrationTableGpio18 != null ? calibrationTableGpio18 : "")
        .withValue("calibrationTableGpio19", calibrationTableGpio19 != null ? calibrationTableGpio19 : "");

    CalibrationCalculator<TestHabState> calculator = new CalibrationCalculator<>();
    TestHabState result = calculator.calculate(state);

    assertEquals(expectedGpio18, result.hrvOutputGpio18(),
        "hrvOutputGpio18 mismatch for scenario: " + scenario);
    assertEquals(expectedGpio19, result.hrvOutputGpio19(),
        "hrvOutputGpio19 mismatch for scenario: " + scenario);
  }
}
