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
        return state.withHrvOutputPower(calculatePower(state));
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
