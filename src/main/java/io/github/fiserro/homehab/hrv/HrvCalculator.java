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

    private final HrvConfig config;

    /**
     * Calculates ventilator power based on aggregated inputs.
     *
     * @param inputs Aggregated sensor inputs by type
     * @return Power level (0-100%)
     */
    public int calculate(Map<HrvInputType, Object> inputs) {
        // Priority 1: Safety override
        if (isTrue(inputs.get(HrvInputType.SMOKE_DETECTOR))) {
            log.info("Smoke detected - max ventilation");
            return config.smokePower();
        }

        if (isTrue(inputs.get(HrvInputType.WINDOW_OPEN))) {
            log.info("Window open - ventilation off");
            return config.windowOpenPower();
        }

        // Priority 2: Manual modes
        if (isTrue(inputs.get(HrvInputType.TEMPORARY_MANUAL_MODE)) ||
            isTrue(inputs.get(HrvInputType.MANUAL_MODE))) {
            Number manualPower = (Number) inputs.get(HrvInputType.MANUAL_POWER);
            int power = manualPower != null ? manualPower.intValue() : config.manualDefaultPower();
            log.info("Manual mode active - power: {}", power);
            return power;
        }

        // Priority 3: Boost modes
        if (isTrue(inputs.get(HrvInputType.TEMPORARY_BOOST_MODE)) ||
            isTrue(inputs.get(HrvInputType.BOOST_MODE))) {
            log.info("Boost mode active");
            return config.boostPower();
        }

        // Priority 4: Exhaust hood
        if (isTrue(inputs.get(HrvInputType.EXHAUST_HOOD))) {
            log.info("Exhaust hood active");
            return config.exhaustHoodPower();
        }

        // Priority 5: Automatic mode based on sensors
        int power = config.basePower();

        Number humidity = (Number) inputs.get(HrvInputType.HUMIDITY);
        if (humidity != null && humidity.doubleValue() > config.humidityThreshold()) {
            power = Math.max(power, config.humidityPower());
            log.debug("High humidity detected: {}% (threshold: {}) - power: {}",
                humidity, config.humidityThreshold(), power);
        }

        Number co2 = (Number) inputs.get(HrvInputType.CO2);
        if (co2 != null && co2.doubleValue() > config.co2Threshold()) {
            power = Math.max(power, config.co2Power());
            log.debug("High CO2 detected: {} ppm (threshold: {}) - power: {}",
                co2, config.co2Threshold(), power);
        }

        log.info("Auto mode - calculated power: {}", power);
        return power;
    }

    private boolean isTrue(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }
}
