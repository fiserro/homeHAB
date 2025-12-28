package io.github.fiserro.homehab.hrv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.fiserro.homehab.TestHabState;
import io.github.fiserro.options.OptionsFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Parameterized tests for {@link IntakeExhaustCalculator}.
 *
 * <p>Tests the intake/exhaust power calculation based on base power and ratio.
 * The ratio controls pressure balance:
 * <ul>
 *   <li>ratio = 0: balanced (intake = exhaust = basePower)</li>
 *   <li>ratio > 0: positive pressure (intake higher)</li>
 *   <li>ratio < 0: negative pressure (exhaust higher)</li>
 * </ul>
 */
class IntakeExhaustCalculatorTest {

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(resources = "/intake-exhaust-calculator-scenarios.csv", numLinesToSkip = 1,
      delimiter = ';')
  @DisplayName("IntakeExhaustCalculator scenario")
  void testScenario(
      String scenario,
      int hrvOutputPower,
      int intakeExhaustRatio,
      int expectedIntake,
      int expectedExhaust) {

    TestHabState state = OptionsFactory.create(TestHabState.class)
        .withValue("hrvOutputPower", hrvOutputPower)
        .withValue("intakeExhaustRatio", intakeExhaustRatio);

    IntakeExhaustCalculator<TestHabState> calculator = new IntakeExhaustCalculator<>();
    TestHabState result = calculator.calculate(state);

    assertEquals(expectedIntake, result.hrvOutputIntake(),
        "hrvOutputIntake mismatch for scenario: " + scenario);
    assertEquals(expectedExhaust, result.hrvOutputExhaust(),
        "hrvOutputExhaust mismatch for scenario: " + scenario);
  }
}
