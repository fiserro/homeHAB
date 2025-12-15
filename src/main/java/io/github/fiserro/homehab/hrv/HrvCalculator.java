package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.HabState;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculation logic for the HRV system.
 * Determines ventilator power based on inputs and configuration.
 */
@Slf4j
public class HrvCalculator implements Calculator {

    @Override
    public HabState calculate(HabState state) {
        return state.withHrvOutputPower(calculatePower(state));
    }

    private int calculatePower(HabState habState) {
        if (habState.manualMode() || habState.temporaryManualMode()) {
            return habState.manualPower();
        }

        if (habState.boostMode() || habState.temporaryBoostMode()) {
            return habState.boostPower();
        }

        if (habState.gas()) {
            return habState.gasPower();
        }

        if (habState.smoke()) {
            return habState.smokePower();
        }

        if (habState.humidity() >= habState.humidityThreshold()) {
            return habState.humidityPower();
        }

        if (habState.co2() >= habState.co2ThresholdHigh()) {
            return habState.co2PowerHigh();
        }

        if (habState.co2() >= habState.co2ThresholdMid()) {
            return habState.co2PowerMid();
        }

        if (habState.co2() >= habState.co2ThresholdLow()) {
            return habState.co2PowerLow();
        }

        return habState.basePower();
    }
}
