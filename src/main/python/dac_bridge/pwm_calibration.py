# PWM Calibration
#
# Calibration tables are loaded from OpenHAB via MQTT.
# Linear interpolation is used to find PWM duty for any target voltage.

import json
import logging

log = logging.getLogger("hrv-bridge")

# Linear calibration (no adjustment - used for TEST mode)
PWM_CALIBRATION_LINEAR = {0: 0.0, 100: 10.0}


def _pwm_for_voltage(target_voltage: float, calibration: dict) -> float:
    """
    Find PWM duty cycle for target voltage using linear interpolation.

    Args:
        target_voltage: Desired output voltage (0-10V)
        calibration: Calibration table {pwm%: voltage}

    Returns:
        PWM duty cycle (0-100%)
    """
    if not calibration or len(calibration) < 2:
        # Fallback to linear if no valid calibration
        return target_voltage * 10.0

    points = sorted(calibration.items(), key=lambda x: x[0])

    # Clamp target to calibration range
    voltages = [v for _, v in points]
    min_v, max_v = min(voltages), max(voltages)
    target_voltage = max(min_v, min(max_v, target_voltage))

    # Find bracketing points
    lower_pwm, lower_v = points[0]
    upper_pwm, upper_v = points[0]

    for pwm, voltage in points:
        if voltage <= target_voltage:
            lower_pwm, lower_v = pwm, voltage
        if voltage >= target_voltage:
            upper_pwm, upper_v = pwm, voltage
            break

    # Edge case: target equals calibration point
    if abs(upper_v - lower_v) < 0.001:
        return float(lower_pwm)

    # Linear interpolation
    ratio = (target_voltage - lower_v) / (upper_v - lower_v)
    return lower_pwm + ratio * (upper_pwm - lower_pwm)


class CalibrationManager:
    """
    Manages PWM calibration tables loaded from MQTT.

    - TEST mode: uses linear calibration (no adjustment)
    - Other modes: uses calibration table from OpenHAB
    """

    def __init__(self):
        self.tables = {18: {}, 19: {}}

    def update_from_mqtt(self, gpio: int, json_str: str) -> bool:
        """
        Update calibration table from MQTT JSON payload.

        Args:
            gpio: GPIO pin number (18 or 19)
            json_str: JSON string {"pwm%": voltage, ...}

        Returns:
            True if successful
        """
        if gpio not in (18, 19):
            log.error(f"Invalid GPIO: {gpio}")
            return False

        try:
            data = json.loads(json_str) if json_str else {}

            # Convert and validate
            new_table = {}
            for k, v in data.items():
                pwm = int(k)
                voltage = float(v)
                if 0 <= pwm <= 100 and 0 <= voltage <= 12:
                    new_table[pwm] = voltage

            self.tables[gpio] = new_table
            log.info(f"GPIO{gpio} calibration: {len(new_table)} points")
            return True

        except (json.JSONDecodeError, ValueError, TypeError) as e:
            log.error(f"Invalid calibration for GPIO{gpio}: {e}")
            return False

    def get_pwm_for_percent(self, gpio: int, percent: float, source: str = "") -> float:
        """
        Get calibrated PWM duty cycle.

        Args:
            gpio: GPIO pin number (18 or 19)
            percent: Desired output (0-100%)
            source: Source name - "test" uses linear, others use calibration

        Returns:
            PWM duty cycle (0-100%)
        """
        target_voltage = (percent / 100.0) * 10.0

        # TEST mode: no calibration adjustment
        if source == "test":
            return _pwm_for_voltage(target_voltage, PWM_CALIBRATION_LINEAR)

        # Use calibration table for this GPIO
        table = self.tables.get(gpio, {})
        if not table or len(table) < 2:
            return _pwm_for_voltage(target_voltage, PWM_CALIBRATION_LINEAR)

        return _pwm_for_voltage(target_voltage, table)
