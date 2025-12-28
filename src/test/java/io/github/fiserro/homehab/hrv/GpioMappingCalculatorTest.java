package io.github.fiserro.homehab.hrv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.fiserro.homehab.TestHabState;
import io.github.fiserro.homehab.module.HrvModule.GpioSource;
import io.github.fiserro.options.OptionsFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Parameterized tests for {@link GpioMappingCalculator}.
 *
 * <p>Tests the mapping of source selection to GPIO output values:
 * <ul>
 *   <li>POWER → hrvOutputPower</li>
 *   <li>INTAKE → hrvOutputIntake</li>
 *   <li>EXHAUST → hrvOutputExhaust</li>
 *   <li>TEST → hrvOutputTest</li>
 *   <li>OFF → 0</li>
 * </ul>
 */
class GpioMappingCalculatorTest {

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(resources = "/gpio-mapping-calculator-scenarios.csv", numLinesToSkip = 1,
      delimiter = ';')
  @DisplayName("GpioMappingCalculator scenario")
  void testScenario(
      String scenario,
      GpioSource sourceGpio18,
      GpioSource sourceGpio19,
      int hrvOutputPower,
      int hrvOutputIntake,
      int hrvOutputExhaust,
      int hrvOutputTest,
      int expectedGpio18,
      int expectedGpio19) {

    TestHabState state = OptionsFactory.create(TestHabState.class)
        .withValue("sourceGpio18", sourceGpio18)
        .withValue("sourceGpio19", sourceGpio19)
        .withValue("hrvOutputPower", hrvOutputPower)
        .withValue("hrvOutputIntake", hrvOutputIntake)
        .withValue("hrvOutputExhaust", hrvOutputExhaust)
        .withValue("hrvOutputTest", hrvOutputTest);

    GpioMappingCalculator<TestHabState> calculator = new GpioMappingCalculator<>();
    TestHabState result = calculator.calculate(state);

    assertEquals(expectedGpio18, result.hrvOutputGpio18(),
        "hrvOutputGpio18 mismatch for scenario: " + scenario);
    assertEquals(expectedGpio19, result.hrvOutputGpio19(),
        "hrvOutputGpio19 mismatch for scenario: " + scenario);
  }
}
