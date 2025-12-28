package io.github.fiserro.homehab.hrv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.fiserro.homehab.TestHabState;
import io.github.fiserro.options.OptionsFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Parameterized tests for {@link PowerCalculator}.
 *
 * <p>Tests the base power calculation logic based on modes (manual, temporary manual, boost),
 * sensor values (humidity, CO2, smoke, gas), and threshold configurations.
 */
class PowerCalculatorTest {

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(resources = "/power-calculator-scenarios.csv", numLinesToSkip = 1, delimiter = ';')
  @DisplayName("PowerCalculator scenario")
  void testScenario(
      String scenario,
      boolean manualMode,
      boolean temporaryManualMode,
      boolean temporaryBoostMode,
      int humidityThreshold,
      int co2ThresholdLow,
      int co2ThresholdMid,
      int co2ThresholdHigh,
      int manualPower,
      int powerLow,
      int powerMid,
      int powerHigh,
      int airHumidity,
      int co2,
      boolean smoke,
      boolean gas,
      int expectedPower) {

    TestHabState state = OptionsFactory.create(TestHabState.class)
        .withValue("manualMode", manualMode)
        .withValue("temporaryManualMode", temporaryManualMode)
        .withValue("temporaryBoostMode", temporaryBoostMode)
        .withValue("humidityThreshold", humidityThreshold)
        .withValue("co2ThresholdLow", co2ThresholdLow)
        .withValue("co2ThresholdMid", co2ThresholdMid)
        .withValue("co2ThresholdHigh", co2ThresholdHigh)
        .withValue("manualPower", manualPower)
        .withValue("powerLow", powerLow)
        .withValue("powerMid", powerMid)
        .withValue("powerHigh", powerHigh)
        .withValue("airHumidity", airHumidity)
        .withValue("co2", co2)
        .withValue("smoke", smoke)
        .withValue("gas", gas);

    PowerCalculator<TestHabState> calculator = new PowerCalculator<>();
    TestHabState result = calculator.calculate(state);

    assertEquals(expectedPower, result.hrvOutputPower(),
        "hrvOutputPower mismatch for scenario: " + scenario);
  }
}
