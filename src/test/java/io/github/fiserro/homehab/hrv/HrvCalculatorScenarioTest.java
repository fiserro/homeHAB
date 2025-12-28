package io.github.fiserro.homehab.hrv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.fiserro.homehab.TestHabState;
import io.github.fiserro.homehab.module.HrvModule.GpioSource;
import io.github.fiserro.options.OptionsFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Parameterized tests for {@link HrvCalculator} using CSV data source.
 *
 * <p>Each row in the CSV file represents one test scenario with all input values
 * and expected output values from HrvModule.
 *
 * <p>CSV file: src/test/resources/hrv-calculator-scenarios.csv
 */
class HrvCalculatorScenarioTest {

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = "/hrv-calculator-scenarios.csv", numLinesToSkip = 1,
                   delimiter = ';', nullValues = {"-1"})
    @DisplayName("HrvCalculator scenario")
    void testScenario(
            String scenario,
            boolean manualMode,
            boolean temporaryManualMode,
            boolean temporaryBoostMode,
            int humidityThreshold,
            int co2ThresholdLow,
            int co2ThresholdMid,
            int co2ThresholdHigh,
            int intakeExhaustRatio,
            int manualPower,
            int powerLow,
            int powerMid,
            int powerHigh,
            int airHumidity,
            int co2,
            boolean smoke,
            boolean gas,
            GpioSource sourceGpio18,
            GpioSource sourceGpio19,
            String calibrationTableGpio18,
            String calibrationTableGpio19,
            Integer hrvOutputTest,
            int expectedPower,
            int expectedIntake,
            int expectedExhaust,
            int expectedGpio18,
            int expectedGpio19) {

        // Create state with input values
        TestHabState state = OptionsFactory.create(TestHabState.class)
                .withValue("manualMode", manualMode)
                .withValue("temporaryManualMode", temporaryManualMode)
                .withValue("temporaryBoostMode", temporaryBoostMode)
                .withValue("humidityThreshold", humidityThreshold)
                .withValue("co2ThresholdLow", co2ThresholdLow)
                .withValue("co2ThresholdMid", co2ThresholdMid)
                .withValue("co2ThresholdHigh", co2ThresholdHigh)
                .withValue("intakeExhaustRatio", intakeExhaustRatio)
                .withValue("manualPower", manualPower)
                .withValue("powerLow", powerLow)
                .withValue("powerMid", powerMid)
                .withValue("powerHigh", powerHigh)
                .withValue("airHumidity", airHumidity)
                .withValue("co2", co2)
                .withValue("smoke", smoke)
                .withValue("gas", gas)
                .withValue("sourceGpio18", sourceGpio18)
                .withValue("sourceGpio19", sourceGpio19)
                .withValue("calibrationTableGpio18", calibrationTableGpio18 != null ? calibrationTableGpio18 : "")
                .withValue("calibrationTableGpio19", calibrationTableGpio19 != null ? calibrationTableGpio19 : "")
                .withValue("hrvOutputTest", hrvOutputTest != null ? hrvOutputTest : 0);

        // Run calculator
        HrvCalculator<TestHabState> calculator = new HrvCalculator<>();
        TestHabState result = calculator.calculate(state);

        // Verify all output values
        assertEquals(expectedPower, result.hrvOutputPower(),
                "hrvOutputPower mismatch for scenario: " + scenario);
        assertEquals(expectedIntake, result.hrvOutputIntake(),
                "hrvOutputIntake mismatch for scenario: " + scenario);
        assertEquals(expectedExhaust, result.hrvOutputExhaust(),
                "hrvOutputExhaust mismatch for scenario: " + scenario);
        assertEquals(expectedGpio18, result.hrvOutputGpio18(),
                "hrvOutputGpio18 mismatch for scenario: " + scenario);
        assertEquals(expectedGpio19, result.hrvOutputGpio19(),
                "hrvOutputGpio19 mismatch for scenario: " + scenario);
    }
}
