# PWM Calibration Tables
#
# Separate calibration tables for each GPIO pin (PWM module).
# Each table maps PWM duty cycle % to measured output voltage.
# Fill in with actual measured values, then the bridge will use
# inverse interpolation to find the correct PWM duty for desired voltage.
#
# How to calibrate:
# 1. Deploy without calibration (happy day values below)
# 2. Set OpenHAB to 10%, 20%, ... 100% and measure voltage on each output
# 3. Write measured voltages to the appropriate table below
# 4. Re-deploy
#
# Format: {pwm_duty_percent: measured_voltage}
# Happy day values assume linear 0-100% -> 0-10V
#
# Usage:
# - Single motor mode: GPIO 18 only (both motors connected to same PWM)
# - Dual motor mode: GPIO 18 (intake) + GPIO 19 (exhaust)

# Calibration table for GPIO 18 (default/intake)
PWM_CALIBRATION_GPIO18 = {
    0: 0.0,
    3: 0.01,
    4: 0.02,
    5: 0.37,
    6: 0.68,
    7: 0.92,
    8: 1.23,
    9: 1.34,
    10: 1.48,
    15: 2.06,
    20: 2.63,
    25: 3.19,
    30: 3.77,
    35: 4.32,
    40: 4.89,
    45: 5.43,
    50: 5.99,
    55: 6.54,
    60: 7.09,
    65: 7.63,
    70: 8.19,
    75: 8.72,
    80: 9.28,
    85: 9.82,
    90: 10.17,
    95: 10.18,
    100: 10.19,
}

# Calibration table for GPIO 19 (exhaust in dual motor mode)
# TODO: Measure and fill in actual values for second PWM module
PWM_CALIBRATION_GPIO19 = {
    0: 0.0,
    3: 0.01,
    4: 0.02,
    5: 0.37,
    6: 0.68,
    7: 0.92,
    8: 1.23,
    9: 1.34,
    10: 1.48,
    15: 2.06,
    20: 2.63,
    25: 3.19,
    30: 3.77,
    35: 4.32,
    40: 4.89,
    45: 5.43,
    50: 5.99,
    55: 6.54,
    60: 7.09,
    65: 7.63,
    70: 8.19,
    75: 8.72,
    80: 9.28,
    85: 9.82,
    90: 10.17,
    95: 10.18,
    100: 10.19,
}

# Linear calibration (for measuring raw PWM output)
PWM_CALIBRATION_LINEAR = {
    0: 0.0,
    100: 10.0,
}

# Active calibration tables for each GPIO pin
# Switch to PWM_CALIBRATION_LINEAR for measuring raw PWM output
ACTIVE_CALIBRATION_GPIO18 = PWM_CALIBRATION_GPIO18
ACTIVE_CALIBRATION_GPIO19 = PWM_CALIBRATION_GPIO19
# ACTIVE_CALIBRATION_GPIO18 = PWM_CALIBRATION_LINEAR  # uncomment for measuring
# ACTIVE_CALIBRATION_GPIO19 = PWM_CALIBRATION_LINEAR  # uncomment for measuring


def _pwm_for_voltage(target_voltage: float, calibration: dict) -> float:
    """
    Find PWM duty cycle needed for target voltage using inverse interpolation.

    Args:
        target_voltage: Desired output voltage (0-10V)
        calibration: Calibration table to use

    Returns:
        PWM duty cycle (0-100%) that produces the target voltage
    """
    # Sort by PWM duty (keys), build list of (pwm, voltage) pairs
    points = sorted(calibration.items(), key=lambda x: x[0])

    # Clamp target to calibration range
    voltages = [v for _, v in points]
    min_voltage = min(voltages)
    max_voltage = max(voltages)
    target_voltage = max(min_voltage, min(max_voltage, target_voltage))

    # Find two points where voltage brackets the target
    lower_pwm, lower_v = points[0]
    upper_pwm, upper_v = points[0]

    for i, (pwm, voltage) in enumerate(points):
        if voltage <= target_voltage:
            lower_pwm, lower_v = pwm, voltage
        if voltage >= target_voltage:
            upper_pwm, upper_v = pwm, voltage
            break
        # If we're at the last point and haven't found upper, use it
        if i == len(points) - 1:
            upper_pwm, upper_v = pwm, voltage

    # Handle edge case where target equals a calibration point
    if abs(upper_v - lower_v) < 0.001:
        return lower_pwm

    # Linear interpolation
    ratio = (target_voltage - lower_v) / (upper_v - lower_v)
    return lower_pwm + ratio * (upper_pwm - lower_pwm)


def percent_to_pwm_gpio18(percent: float) -> float:
    """
    Convert desired output percent to calibrated PWM duty cycle for GPIO 18.

    Args:
        percent: Desired output (0-100%, where 100% = 10V)

    Returns:
        PWM duty cycle to send to the module
    """
    target_voltage = (percent / 100.0) * 10.0  # 100% = 10V
    return _pwm_for_voltage(target_voltage, ACTIVE_CALIBRATION_GPIO18)


def percent_to_pwm_gpio19(percent: float) -> float:
    """
    Convert desired output percent to calibrated PWM duty cycle for GPIO 19.

    Args:
        percent: Desired output (0-100%, where 100% = 10V)

    Returns:
        PWM duty cycle to send to the module
    """
    target_voltage = (percent / 100.0) * 10.0  # 100% = 10V
    return _pwm_for_voltage(target_voltage, ACTIVE_CALIBRATION_GPIO19)


# Legacy function for backwards compatibility
def percent_to_pwm(percent: float) -> float:
    """
    Convert desired output percent to calibrated PWM duty cycle.
    Uses GPIO 18 calibration for backwards compatibility.

    Args:
        percent: Desired output (0-100%, where 100% = 10V)

    Returns:
        PWM duty cycle to send to the module
    """
    return percent_to_pwm_gpio18(percent)
