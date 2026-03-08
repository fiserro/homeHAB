#pragma once

#include <Arduino.h>
#include "config.h"

/**
 * AC current sensor using SCT013 and ESP32-C3 built-in ADC.
 *
 * The SCT013 output is AC voltage centered around a DC bias point
 * (set by external voltage divider). This class samples the signal,
 * calculates RMS, and converts to power in Watts.
 */
class CurrentSensor {
public:
    /**
     * Initialize current sensor.
     * @param pin GPIO pin connected to SCT013 output (via bias circuit)
     * @param channelIndex Logical channel index (for calibration lookup)
     */
    void begin(uint8_t pin, uint8_t channelIndex);

    /**
     * Read power in Watts (single measurement).
     *
     * Performs full measurement cycle:
     * 1. Sample ADC N times at 10kHz
     * 2. Filter outliers (remove top/bottom 10%)
     * 3. Detect floating input (low variance)
     * 4. Calculate DC bias from filtered mean
     * 5. Calculate RMS voltage of AC component
     * 6. Convert to power via SCT013 ratio and mains voltage
     * 7. Apply noise threshold and max power check
     *
     * @return Power in Watts, or 0 if below noise threshold / sensor disconnected
     */
    float readPowerWatts();

    /**
     * Read power with EMA (Exponential Moving Average) smoothing.
     * Call this repeatedly at regular intervals for stable readings.
     *
     * @return Smoothed power in Watts (integer-rounded)
     */
    int readPowerFiltered();

private:
    uint8_t _pin = 0;
    uint8_t _channelIndex = 0;
    float _emaPower = 0.0f;

    /** Read raw ADC samples in millivolts. */
    void readSamples(float* buffer, size_t count);

    /**
     * Filter outliers by removing top/bottom percentile.
     * @param buffer Input samples (will be sorted in-place)
     * @param count Number of input samples
     * @param outStart Output: index of first valid sample after filtering
     * @param outEnd Output: index past last valid sample after filtering
     */
    void filterOutliers(float* buffer, size_t count, size_t& outStart, size_t& outEnd);

    /** Calculate RMS of AC component (bias-subtracted). */
    float calculateRms(const float* samples, size_t start, size_t end);
};
