#include "current_sensor.h"
#include <algorithm>
#include <math.h>

void CurrentSensor::begin(uint8_t pin, uint8_t channelIndex) {
    _pin = pin;
    _channelIndex = channelIndex;
    _emaPower = 0.0f;
    // Set default attenuation to 11dB (0-2500mV range) for all ADC channels.
    // analogReadMilliVolts() handles calibration internally.
    analogSetAttenuation(ADC_11db);
    // Perform a dummy read to initialize the ADC pin.
    analogReadMilliVolts(_pin);
}

void CurrentSensor::readSamples(float* buffer, size_t count) {
    for (size_t i = 0; i < count; i++) {
        buffer[i] = (float)analogReadMilliVolts(_pin);
        delayMicroseconds(ADC_SAMPLE_INTERVAL_US);
    }
}

void CurrentSensor::filterOutliers(float* buffer, size_t count,
                                    size_t& outStart, size_t& outEnd) {
    std::sort(buffer, buffer + count);
    size_t trim = count * SPIKE_FILTER_PERCENT / 100;
    if (trim < 1) trim = 1;
    outStart = trim;
    outEnd = count - trim;
}

float CurrentSensor::calculateRms(const float* samples, size_t start, size_t end) {
    size_t n = end - start;
    if (n == 0) return 0.0f;

    // Calculate mean (DC bias) from filtered samples
    float sum = 0.0f;
    for (size_t i = start; i < end; i++) {
        sum += samples[i];
    }
    float bias = sum / (float)n;

    // Calculate RMS of AC component
    float sumSquares = 0.0f;
    for (size_t i = start; i < end; i++) {
        float ac = samples[i] - bias;
        sumSquares += ac * ac;
    }

    return sqrtf(sumSquares / (float)n);
}

float CurrentSensor::readPowerWatts() {
    // 1. Sample ADC
    float samples[ADC_SAMPLES];
    readSamples(samples, ADC_SAMPLES);

    // 2. Filter outliers (sorts samples in-place)
    size_t start, end;
    filterOutliers(samples, ADC_SAMPLES, start, end);
    size_t n = end - start;
    if (n < 10) return 0.0f;

    // 3. Detect floating/disconnected input (low variance = no real AC signal)
    float sum = 0.0f;
    for (size_t i = start; i < end; i++) {
        sum += samples[i];
    }
    float mean = sum / (float)n;

    float variance = 0.0f;
    for (size_t i = start; i < end; i++) {
        float diff = samples[i] - mean;
        variance += diff * diff;
    }
    variance /= (float)n;

    if (variance < MIN_VARIANCE_MV2) {
        return 0.0f;
    }

    // 4. Calculate RMS voltage (already filtered, reuse start/end)
    float rmsMillivolts = calculateRms(samples, start, end);
    float rmsVolts = rmsMillivolts / 1000.0f;

    // 5. Convert to current and power
    float current = rmsVolts * SCT013_RATIO;
    float power = current * MAINS_VOLTAGE;

    // 6. Apply channel calibration
    if (_channelIndex < sizeof(CHANNEL_CALIBRATION) / sizeof(CHANNEL_CALIBRATION[0])) {
        power *= CHANNEL_CALIBRATION[_channelIndex];
    }

    // 7. Apply noise threshold
    if (power < NOISE_THRESHOLD_W) {
        return 0.0f;
    }

    // 8. Detect disconnected sensor (floating reads high)
    if (power > MAX_POWER_W) {
        return 0.0f;
    }

    return power;
}

int CurrentSensor::readPowerFiltered() {
    float raw = readPowerWatts();

    if (raw == 0.0f) {
        // Below noise threshold -> immediately zero
        _emaPower = 0.0f;
    } else if (_emaPower == 0.0f) {
        // Transition from zero to non-zero -> set immediately
        _emaPower = raw;
    } else if (fabsf(raw - _emaPower) > SPIKE_RESET_THRESHOLD) {
        // Large change -> reset EMA to follow quickly
        _emaPower = raw;
    } else {
        // Normal EMA smoothing
        _emaPower = EMA_ALPHA * raw + (1.0f - EMA_ALPHA) * _emaPower;
    }

    return (int)roundf(_emaPower);
}
