package io.github.fiserro.homehab.hrv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * Calculation logic for the HRV system.
 * Determines ventilator power based on inputs and configuration.
 */
@Slf4j
@RequiredArgsConstructor
public class HrvCalculator {


    public int calculate(HrvState state) {
        if (state.manualMode() || state.temporaryManualMode()) {
            return state.manualPower();
        }

        if (state.boostMode() || state.temporaryBoostMode()) {
            return state.boostPower();
        }

        if (state.gas()) {
            return state.gasPower();
        }

        if (state.smoke()) {
            return state.smokePower();
        }

        if (state.humidity() >= state.humidityThreshold()) {
            return state.humidityPower();
        }

        if (state.co2() >= state.co2ThresholdHigh()) {
            return state.co2PowerHigh();
        }

        if (state.co2() >= state.co2ThresholdMid()) {
            return state.co2PowerMid();
        }

        if (state.co2() >= state.co2ThresholdLow()) {
            return state.co2PowerLow();
        }

        return state.basePower();
    }

}
