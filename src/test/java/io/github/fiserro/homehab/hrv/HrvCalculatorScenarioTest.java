package io.github.fiserro.homehab.hrv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.fiserro.homehab.TestHabState;
import io.github.fiserro.homehab.module.HrvModule.GpioSource;
import io.github.fiserro.options.OptionsFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Integration tests for {@link HrvCalculator} using CSV data source.
 *
 * <p>Tests the full calculation chain: PowerCalculator → IntakeExhaustCalculator → CalibrationCalculator.
 * Each row represents a scenario that exercises multiple calculators together.
 *
 * <p>For unit tests of individual calculators, see:
 * <ul>
 *   <li>{@link PowerCalculatorTest}</li>
 *   <li>{@link IntakeExhaustCalculatorTest}</li>
 *   <li>{@link CalibrationCalculatorTest}</li>
 * </ul>
 */
class HrvCalculatorScenarioTest {

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = "/hrv-calculator-scenarios.csv", numLinesToSkip = 1,
                   delimiter = ';', nullValues = {"-", "-1"})
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
            GpioSource sourceGpio12,
            GpioSource sourceGpio13,
            String calibrationTableGpio12,
            String calibrationTableGpio13,
            Integer hrvOutputTest,
            int expectedPower,
            int expectedIntake,
            int expectedExhaust,
            int expectedGpio12,
            int expectedGpio13) {

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
                .withValue("sourceGpio12", sourceGpio12)
                .withValue("sourceGpio13", sourceGpio13)
                .withValue("calibrationTableGpio12", calibrationTableGpio12 != null ? calibrationTableGpio12 : "")
                .withValue("calibrationTableGpio13", calibrationTableGpio13 != null ? calibrationTableGpio13 : "")
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
        assertEquals(expectedGpio12, result.hrvOutputGpio12(),
                "hrvOutputGpio12 mismatch for scenario: " + scenario);
        assertEquals(expectedGpio13, result.hrvOutputGpio13(),
                "hrvOutputGpio13 mismatch for scenario: " + scenario);
    }
}
