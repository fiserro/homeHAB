package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.module.HrvModule;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculation logic for the HRV system.
 * Determines ventilator power based on inputs and configuration.
 *
 * <p>This calculator works with any implementation of {@link HrvModule},
 * making it reusable across different home automation setups.
 *
 * @param <T> the module type (extends HrvModule)
 */
@Slf4j
public class HrvCalculator<T extends HrvModule<T>> implements Calculator<T> {

    @Override
    public T calculate(T state) {
        int basePower = calculatePower(state);
        int ratio = state.intakeExhaustRatio();

        // Calculate intake and exhaust power based on ratio
        // ratio 50 = balanced, <50 = more exhaust, >50 = more intake
        // The adjustment is percentage-based: ratio 55 means intake gets +5%, exhaust gets -5%
        int intakePower = adjustPower(basePower, ratio - 50);
        int exhaustPower = adjustPower(basePower, 50 - ratio);

        return state
            .withHrvOutputPower(basePower)
            .withHrvOutputIntake(intakePower)
            .withHrvOutputExhaust(exhaustPower);
    }

    /**
     * Adjust power level by the given delta, clamping to valid range 0-100.
     */
    private int adjustPower(int basePower, int delta) {
        return Math.max(0, Math.min(100, basePower + delta));
    }

    /**
     * Calculate the HRV power level based on current state.
     * This method is public to allow direct use without type casting issues.
     *
     * @param state the HRV module state
     * @return calculated power level (0-100)
     */
    public int calculatePower(HrvModule<?> state) {
        if (state.manualMode() || state.temporaryManualMode()) {
            return state.manualPower();
        }

        if (state.temporaryBoostMode()) {
            return state.powerHigh();
        }

        if (state.gas()) {
            return state.powerHigh();
        }

        if (state.smoke()) {
            return HrvModule.POWER_OFF;
        }

        if (state.airHumidity() >= state.humidityThreshold()) {
            return state.powerHigh();
        }

        if (state.co2() >= state.co2ThresholdHigh()) {
            return state.powerHigh();
        }

        if (state.co2() >= state.co2ThresholdMid()) {
            return state.powerMid();
        }

        if (state.co2() >= state.co2ThresholdLow()) {
            return state.powerLow();
        }

        return state.powerLow();
    }
}
