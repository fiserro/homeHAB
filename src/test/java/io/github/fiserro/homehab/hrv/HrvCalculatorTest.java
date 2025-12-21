package io.github.fiserro.homehab.hrv;

import static org.junit.jupiter.api.Assertions.*;

import io.github.fiserro.homehab.TestHabState;
import io.github.fiserro.homehab.HabStateFactory;
import io.github.fiserro.homehab.module.HrvModule;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link HrvCalculator}.
 *
 * <p>HrvCalculator uses priority-based logic:
 * <ol>
 *   <li>Manual/Temporary manual mode → manualPower</li>
 *   <li>Temporary boost mode → powerHigh</li>
 *   <li>Gas detected → powerHigh</li>
 *   <li>Smoke detected → POWER_OFF (0)</li>
 *   <li>Humidity >= threshold → powerHigh</li>
 *   <li>CO2 >= high threshold → powerHigh</li>
 *   <li>CO2 >= mid threshold → powerMid</li>
 *   <li>CO2 >= low threshold → powerLow</li>
 *   <li>Default → powerLow</li>
 * </ol>
 */
class HrvCalculatorTest {

    private HrvCalculator<TestHabState> calculator;
    private Map<String, State> itemStates;

    @BeforeEach
    void setUp() {
        calculator = new HrvCalculator<>();
        itemStates = new HashMap<>();
    }

    private TestHabState createState() {
        return HabStateFactory.of(TestHabState.class, itemStates);
    }

    private void setItem(String name, int value) {
        State state = mock(State.class);
        when(state.as(DecimalType.class)).thenReturn(new DecimalType(value));
        itemStates.put(name, state);
    }

    private void setItem(String name, boolean value) {
        State state = mock(State.class);
        when(state.as(OnOffType.class)).thenReturn(value ? OnOffType.ON : OnOffType.OFF);
        itemStates.put(name, state);
    }

    @Nested
    @DisplayName("Priority 1: Manual Mode")
    class ManualModeTests {

        @Test
        @DisplayName("Manual mode returns manualPower")
        void manualModeReturnsManualPower() {
            setItem("manualMode", true);
            setItem("manualPower", 50);

            TestHabState result = calculator.calculate(createState());

            assertEquals(50, result.hrvOutputPower());
        }

        @Test
        @DisplayName("Temporary manual mode returns manualPower")
        void temporaryManualModeReturnsManualPower() {
            setItem("temporaryManualMode", true);
            setItem("manualPower", 60);

            TestHabState result = calculator.calculate(createState());

            assertEquals(60, result.hrvOutputPower());
        }

        @Test
        @DisplayName("Manual mode has priority over all other conditions")
        void manualModeHasPriorityOverAllConditions() {
            setItem("manualMode", true);
            setItem("manualPower", 40);
            // Set all other conditions that would normally trigger high power
            setItem("temporaryBoostMode", true);
            setItem("gas", true);
            setItem("smoke", true);
            setItem("airHumidity", 100);
            setItem("co2", 2000);

            TestHabState result = calculator.calculate(createState());

            assertEquals(40, result.hrvOutputPower());
        }
    }

    @Nested
    @DisplayName("Priority 2: Temporary Boost Mode")
    class TemporaryBoostModeTests {

        @Test
        @DisplayName("Temporary boost mode returns powerHigh")
        void temporaryBoostModeReturnsPowerHigh() {
            setItem("temporaryBoostMode", true);
            setItem("powerHigh", 100);

            TestHabState result = calculator.calculate(createState());

            assertEquals(100, result.hrvOutputPower());
        }

        @Test
        @DisplayName("Temporary boost has priority over gas, smoke, humidity, CO2")
        void temporaryBoostHasPriorityOverLowerConditions() {
            setItem("temporaryBoostMode", true);
            setItem("powerHigh", 95);
            setItem("gas", true);
            setItem("smoke", true);
            setItem("airHumidity", 100);

            TestHabState result = calculator.calculate(createState());

            assertEquals(95, result.hrvOutputPower());
        }
    }

    @Nested
    @DisplayName("Priority 3: Gas Detection")
    class GasDetectionTests {

        @Test
        @DisplayName("Gas detection returns powerHigh")
        void gasDetectionReturnsPowerHigh() {
            setItem("gas", true);
            setItem("powerHigh", 100);

            TestHabState result = calculator.calculate(createState());

            assertEquals(100, result.hrvOutputPower());
        }

        @Test
        @DisplayName("Gas has priority over smoke (ventilate to clear gas)")
        void gasHasPriorityOverSmoke() {
            setItem("gas", true);
            setItem("smoke", true);
            setItem("powerHigh", 100);

            TestHabState result = calculator.calculate(createState());

            assertEquals(100, result.hrvOutputPower());
        }
    }

    @Nested
    @DisplayName("Priority 4: Smoke Detection")
    class SmokeDetectionTests {

        @Test
        @DisplayName("Smoke detection returns POWER_OFF")
        void smokeDetectionReturnsPowerOff() {
            setItem("smoke", true);

            TestHabState result = calculator.calculate(createState());

            assertEquals(HrvModule.POWER_OFF, result.hrvOutputPower());
        }

        @Test
        @DisplayName("Smoke has priority over humidity and CO2")
        void smokeHasPriorityOverHumidityAndCo2() {
            setItem("smoke", true);
            setItem("airHumidity", 100);
            setItem("co2", 2000);

            TestHabState result = calculator.calculate(createState());

            assertEquals(HrvModule.POWER_OFF, result.hrvOutputPower());
        }
    }

    @Nested
    @DisplayName("Priority 5: Humidity Threshold")
    class HumidityThresholdTests {

        @Test
        @DisplayName("Humidity at threshold returns powerHigh")
        void humidityAtThresholdReturnsPowerHigh() {
            setItem("airHumidity", 60);  // default threshold is 60
            setItem("humidityThreshold", 60);
            setItem("powerHigh", 100);

            TestHabState result = calculator.calculate(createState());

            assertEquals(100, result.hrvOutputPower());
        }

        @Test
        @DisplayName("Humidity above threshold returns powerHigh")
        void humidityAboveThresholdReturnsPowerHigh() {
            setItem("airHumidity", 75);
            setItem("humidityThreshold", 60);
            setItem("powerHigh", 100);

            TestHabState result = calculator.calculate(createState());

            assertEquals(100, result.hrvOutputPower());
        }

        @Test
        @DisplayName("Humidity below threshold continues to CO2 check")
        void humidityBelowThresholdContinuesToCo2Check() {
            setItem("airHumidity", 50);
            setItem("humidityThreshold", 60);
            setItem("co2", 800);  // above mid threshold (700)
            setItem("powerMid", 75);

            TestHabState result = calculator.calculate(createState());

            assertEquals(75, result.hrvOutputPower());
        }
    }

    @Nested
    @DisplayName("Priority 6-8: CO2 Thresholds")
    class Co2ThresholdTests {

        @Test
        @DisplayName("CO2 at high threshold returns powerHigh")
        void co2AtHighThresholdReturnsPowerHigh() {
            setItem("co2", 900);  // default high threshold is 900
            setItem("co2ThresholdHigh", 900);
            setItem("powerHigh", 100);

            TestHabState result = calculator.calculate(createState());

            assertEquals(100, result.hrvOutputPower());
        }

        @Test
        @DisplayName("CO2 above high threshold returns powerHigh")
        void co2AboveHighThresholdReturnsPowerHigh() {
            setItem("co2", 1200);
            setItem("co2ThresholdHigh", 900);
            setItem("powerHigh", 100);

            TestHabState result = calculator.calculate(createState());

            assertEquals(100, result.hrvOutputPower());
        }

        @Test
        @DisplayName("CO2 at mid threshold returns powerMid")
        void co2AtMidThresholdReturnsPowerMid() {
            setItem("co2", 700);  // default mid threshold is 700
            setItem("co2ThresholdMid", 700);
            setItem("co2ThresholdHigh", 900);
            setItem("powerMid", 75);

            TestHabState result = calculator.calculate(createState());

            assertEquals(75, result.hrvOutputPower());
        }

        @Test
        @DisplayName("CO2 between mid and high returns powerMid")
        void co2BetweenMidAndHighReturnsPowerMid() {
            setItem("co2", 800);
            setItem("co2ThresholdMid", 700);
            setItem("co2ThresholdHigh", 900);
            setItem("powerMid", 75);

            TestHabState result = calculator.calculate(createState());

            assertEquals(75, result.hrvOutputPower());
        }

        @Test
        @DisplayName("CO2 at low threshold returns powerLow")
        void co2AtLowThresholdReturnsPowerLow() {
            setItem("co2", 500);  // default low threshold is 500
            setItem("co2ThresholdLow", 500);
            setItem("co2ThresholdMid", 700);
            setItem("co2ThresholdHigh", 900);
            setItem("powerLow", 50);

            TestHabState result = calculator.calculate(createState());

            assertEquals(50, result.hrvOutputPower());
        }

        @Test
        @DisplayName("CO2 between low and mid returns powerLow")
        void co2BetweenLowAndMidReturnsPowerLow() {
            setItem("co2", 600);
            setItem("co2ThresholdLow", 500);
            setItem("co2ThresholdMid", 700);
            setItem("co2ThresholdHigh", 900);
            setItem("powerLow", 50);

            TestHabState result = calculator.calculate(createState());

            assertEquals(50, result.hrvOutputPower());
        }
    }

    @Nested
    @DisplayName("Default Behavior")
    class DefaultBehaviorTests {

        @Test
        @DisplayName("CO2 below all thresholds returns powerLow")
        void co2BelowAllThresholdsReturnsPowerLow() {
            setItem("co2", 400);
            setItem("co2ThresholdLow", 500);
            setItem("powerLow", 50);

            TestHabState result = calculator.calculate(createState());

            assertEquals(50, result.hrvOutputPower());
        }

        @Test
        @DisplayName("All defaults with no sensor data returns powerLow")
        void allDefaultsReturnsPowerLow() {
            // No items set - all defaults
            TestHabState result = calculator.calculate(createState());

            // Default co2 is 500 which equals co2ThresholdLow (500), so powerLow
            assertEquals(15, result.hrvOutputPower());  // default powerLow is 15
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Custom power levels are respected")
        void customPowerLevelsAreRespected() {
            setItem("co2", 1000);
            setItem("co2ThresholdHigh", 900);
            setItem("powerHigh", 80);  // Custom power level

            TestHabState result = calculator.calculate(createState());

            assertEquals(80, result.hrvOutputPower());
        }

        @Test
        @DisplayName("Zero manual power is valid")
        void zeroManualPowerIsValid() {
            setItem("manualMode", true);
            setItem("manualPower", 0);

            TestHabState result = calculator.calculate(createState());

            assertEquals(0, result.hrvOutputPower());
        }

        @Test
        @DisplayName("Maximum power (100) is valid")
        void maximumPowerIsValid() {
            setItem("temporaryBoostMode", true);
            setItem("powerHigh", 100);

            TestHabState result = calculator.calculate(createState());

            assertEquals(100, result.hrvOutputPower());
        }
    }
}
