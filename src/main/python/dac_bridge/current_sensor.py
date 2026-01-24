# -*- coding: utf-8 -*-
"""
SCT013 Current Sensor Module

Reads AC current from SCT013 current transformers connected to ADS1256 ADC.

SCT013-005 (5A/1V) specifications:
- Input: 0-5A AC
- Output: 0-1V AC (proportional to current)
- With bias circuit (voltage divider): signal centered at ~1.65V

Measurement principle:
1. Sample voltage multiple times over at least one AC cycle (50Hz = 20ms)
2. Remove DC bias (average value)
3. Calculate RMS (Root Mean Square)
4. Convert voltage to current using sensor ratio
"""

import math
import time
import logging

log = logging.getLogger("current-sensor")

# SCT013-005 specifications
SCT013_RATIO = 5.0  # 5A input produces 1V output
SCT013_MAX_VOLTAGE = 1.0  # Max output voltage at max current

# Default measurement parameters
DEFAULT_SAMPLES = 200  # Number of samples per measurement
DEFAULT_SAMPLE_INTERVAL = 0.0001  # 100us between samples (10kHz effective rate)

# Mains voltage for power calculation
MAINS_VOLTAGE = 230.0  # V

# Noise filtering parameters
NOISE_THRESHOLD_WATTS = 3.0  # Below this, report 0W
MAX_POWER_WATTS = 500.0  # Above this, consider sensor disconnected (floating input)
SPIKE_FILTER_PERCENTILE = 10  # Remove top/bottom N% of samples as outliers
EMA_ALPHA = 0.3  # Exponential moving average smoothing factor (0-1, lower = smoother)

# Calibration factors per ADC channel (compensates for hardware differences)
# AD1 reads ~18% higher than AD0, so we multiply AD1 by 0.85
CHANNEL_CALIBRATION = {
    0: 1.0,    # AD0 - reference
    1: 0.85,   # AD1 - calibrated to match AD0
    2: 1.0,
    3: 1.0,
    4: 1.0,
    5: 1.0,
    6: 1.0,
    7: 1.0,
}


class CurrentSensor:
    """
    AC current sensor using SCT013 and ADS1256 ADC.

    The SCT013 output is AC voltage centered around a DC bias point.
    This class samples the signal, calculates RMS, and converts to current.
    """

    def __init__(self, adc, channel: int, ratio: float = SCT013_RATIO,
                 samples: int = DEFAULT_SAMPLES):
        """
        Initialize current sensor.

        Args:
            adc: ADS1256 ADC instance
            channel: ADC channel (0-7) where sensor is connected
            ratio: Current to voltage ratio (A/V), default 5.0 for SCT013-005
            samples: Number of samples per measurement
        """
        self.adc = adc
        self.channel = channel
        self.ratio = ratio
        self.samples = samples
        self._bias_voltage = None

    def calibrate_bias(self, num_readings: int = 10) -> float:
        """
        Calibrate the DC bias voltage.

        Should be called when no current is flowing through the sensor.
        This measures the voltage divider's center point.

        Args:
            num_readings: Number of readings to average

        Returns:
            Measured bias voltage
        """
        total = 0.0
        for _ in range(num_readings):
            total += self.adc.read_channel_voltage(self.channel)
            time.sleep(0.01)  # 10ms between readings

        self._bias_voltage = total / num_readings
        log.info(f"Channel {self.channel} bias calibrated: {self._bias_voltage:.4f}V")
        return self._bias_voltage

    def read_raw_samples(self) -> list[float]:
        """
        Read raw voltage samples from ADC.

        Returns:
            List of voltage readings
        """
        samples = []
        for _ in range(self.samples):
            voltage = self.adc.read_channel_voltage(self.channel)
            samples.append(voltage)
            time.sleep(DEFAULT_SAMPLE_INTERVAL)
        return samples

    def _filter_outliers(self, samples: list[float], percentile: int = SPIKE_FILTER_PERCENTILE) -> list[float]:
        """
        Remove outliers from samples using percentile filtering.

        Removes top and bottom N% of samples to eliminate spikes.

        Args:
            samples: List of voltage readings
            percentile: Percentage of samples to remove from each end

        Returns:
            Filtered list of samples
        """
        if len(samples) < 10:
            return samples

        sorted_samples = sorted(samples)
        trim_count = len(samples) * percentile // 100
        if trim_count < 1:
            trim_count = 1

        return sorted_samples[trim_count:-trim_count]

    def calculate_rms(self, samples: list[float], bias: float | None = None) -> float:
        """
        Calculate RMS voltage from samples.

        Args:
            samples: List of voltage readings
            bias: DC bias to subtract (if None, uses mean of samples)

        Returns:
            RMS voltage (AC component only)
        """
        if not samples:
            return 0.0

        # Filter outliers (spikes from connecting/disconnecting)
        filtered = self._filter_outliers(samples)

        # Remove DC bias
        if bias is None:
            bias = sum(filtered) / len(filtered)

        # Calculate RMS of AC component
        sum_squares = sum((s - bias) ** 2 for s in filtered)
        rms = math.sqrt(sum_squares / len(filtered))

        return rms

    def read_current(self) -> float:
        """
        Measure AC current (single measurement).

        Returns:
            Current in Amperes (RMS)
        """
        samples = self.read_raw_samples()

        # Use calibrated bias if available, otherwise calculate from samples
        bias = self._bias_voltage if self._bias_voltage else None
        rms_voltage = self.calculate_rms(samples, bias)

        # Convert voltage to current
        # SCT013 produces 1V per 5A, so current = voltage * ratio
        current = rms_voltage * self.ratio

        return current

    def read_current_averaged(self, num_readings: int = 3) -> float:
        """
        Measure AC current with averaging for more stable readings.

        Args:
            num_readings: Number of measurements to average

        Returns:
            Averaged current in Amperes (RMS)
        """
        total = 0.0
        for _ in range(num_readings):
            total += self.read_current()
        return total / num_readings

    def read_power(self) -> float:
        """
        Measure AC power in Watts (single measurement).

        Returns:
            Power in Watts (assuming resistive load, PF=1)
        """
        current = self.read_current()
        power = current * MAINS_VOLTAGE

        # Apply channel calibration
        cal_factor = CHANNEL_CALIBRATION.get(self.channel, 1.0)
        power = power * cal_factor

        # Apply noise threshold
        if power < NOISE_THRESHOLD_WATTS:
            return 0.0

        # Detect disconnected sensor (floating input reads high values)
        if power > MAX_POWER_WATTS:
            return 0.0

        return power

    def is_current_flowing(self, threshold: float = 0.1) -> bool:
        """
        Quick check if significant current is flowing.

        Args:
            threshold: Minimum current (A) to consider as "flowing"

        Returns:
            True if current above threshold
        """
        current = self.read_current()
        return current > threshold


class CurrentMonitor:
    """
    Monitors multiple current sensors and provides aggregated readings.
    """

    def __init__(self, adc, channels: list[int], ratio: float = SCT013_RATIO,
                 samples: int = DEFAULT_SAMPLES):
        """
        Initialize current monitor for multiple channels.

        Args:
            adc: ADS1256 ADC instance
            channels: List of ADC channels to monitor
            ratio: Current to voltage ratio for all sensors
            samples: Number of samples per measurement
        """
        self.sensors = {}
        self._ema_power = {}  # Exponential moving average for power readings
        for ch in channels:
            self.sensors[ch] = CurrentSensor(adc, ch, ratio, samples)
            self._ema_power[ch] = 0.0

    def calibrate_all(self, num_readings: int = 10) -> dict[int, float]:
        """
        Calibrate bias for all sensors.

        Returns:
            Dictionary of channel -> bias voltage
        """
        biases = {}
        for ch, sensor in self.sensors.items():
            biases[ch] = sensor.calibrate_bias(num_readings)
        return biases

    def read_all(self) -> dict[int, float]:
        """
        Read current from all sensors.

        Returns:
            Dictionary of channel -> current (A)
        """
        readings = {}
        for ch, sensor in self.sensors.items():
            readings[ch] = sensor.read_current()
        return readings

    def read_all_averaged(self, num_readings: int = 3) -> dict[int, float]:
        """
        Read averaged current from all sensors.

        Returns:
            Dictionary of channel -> current (A)
        """
        readings = {}
        for ch, sensor in self.sensors.items():
            readings[ch] = sensor.read_current_averaged(num_readings)
        return readings

    def read_all_power(self) -> dict[int, float]:
        """
        Read power from all sensors in Watts.

        Returns:
            Dictionary of channel -> power (W)
        """
        readings = {}
        for ch, sensor in self.sensors.items():
            readings[ch] = sensor.read_power()
        return readings

    def read_all_power_filtered(self) -> dict[int, float]:
        """
        Read power from all sensors with exponential moving average filtering.

        This provides smooth readings without sudden jumps from noise.

        Returns:
            Dictionary of channel -> power (W), smoothed
        """
        readings = {}
        for ch, sensor in self.sensors.items():
            raw_power = sensor.read_power()

            # If raw is 0 (below noise threshold), immediately set to 0
            if raw_power == 0:
                self._ema_power[ch] = 0
            # If EMA was 0 and now we have power, immediately set to new value
            elif self._ema_power[ch] == 0 and raw_power > 0:
                self._ema_power[ch] = raw_power
            # Spike protection: if change is too large, reset EMA to new value
            elif abs(raw_power - self._ema_power[ch]) > 50:
                self._ema_power[ch] = raw_power
            else:
                # Normal EMA smoothing
                self._ema_power[ch] = (EMA_ALPHA * raw_power +
                                       (1 - EMA_ALPHA) * self._ema_power[ch])

            # Round to integer watts
            readings[ch] = round(self._ema_power[ch])

        return readings


### END OF FILE ###
