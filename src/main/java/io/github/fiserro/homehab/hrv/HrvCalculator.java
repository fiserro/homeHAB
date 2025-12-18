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

        if (habState.temporaryBoostMode()) {
            return habState.powerHigh();
        }

        if (habState.gas()) {
            return habState.powerHigh();
        }

        if (habState.smoke()) {
            return HabState.POWER_OFF;
        }

        if (habState.airHumidity() >= habState.humidityThreshold()) {
            return habState.powerHigh();
        }

        if (habState.co2() >= habState.co2ThresholdHigh()) {
            return habState.powerHigh();
        }

        if (habState.co2() >= habState.co2ThresholdMid()) {
            return habState.powerMid();
        }

        if (habState.co2() >= habState.co2ThresholdLow()) {
            return habState.powerLow();
        }

        return habState.powerLow();
    }
}
