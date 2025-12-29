package io.github.fiserro.homehab.hrv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.fiserro.homehab.TestHabState;
import io.github.fiserro.options.OptionsFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Parameterized tests for {@link BypassCalculator}.
 *
 * <p>Tests the automatic bypass valve control based on temperature conditions:
 * <ul>
 *   <li>Skip if manual mode or temporary manual mode is active</li>
 *   <li>Bypass ON when: insideTemp > preferredTemp + hysteresis/2 AND insideTemp > outsideTemp + hysteresis/2</li>
 *   <li>Bypass OFF when: insideTemp < preferredTemp - hysteresis/2 OR insideTemp < outsideTemp - hysteresis/2</li>
 *   <li>Keep current state if in hysteresis zone</li>
 * </ul>
 */
class BypassCalculatorTest {

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(resources = "/bypass-calculator-scenarios.csv", numLinesToSkip = 1, delimiter = ';')
  @DisplayName("BypassCalculator scenario")
  void testScenario(
      String scenario,
      boolean manualMode,
      boolean temporaryManualMode,
      float insideTemperature,
      float outsideTemperature,
      float preferredTemperature,
      float bypassHysteresis,
      boolean currentBypass,
      boolean expectedBypass) {

    TestHabState state = OptionsFactory.create(TestHabState.class)
        .withValue("manualMode", manualMode)
        .withValue("temporaryManualMode", temporaryManualMode)
        .withValue("insideTemperature", insideTemperature)
        .withValue("outsideTemperature", outsideTemperature)
        .withValue("preferredTemperature", preferredTemperature)
        .withValue("bypassHysteresis", bypassHysteresis)
        .withValue("bypass", currentBypass);

    BypassCalculator<TestHabState> calculator = new BypassCalculator<>();
    TestHabState result = calculator.calculate(state);

    assertEquals(expectedBypass, result.bypass(),
        "bypass mismatch for scenario: " + scenario);
  }
}
