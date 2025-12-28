# PWM Calibration

This document describes the PWM calibration system for the HRV (Heat Recovery Ventilator) control.

## Motivation

The HRV system uses PWM (Pulse Width Modulation) signals to control the speed of ventilation motors. The PWM signal is converted to a 0-10V analog voltage using a DAC (Digital-to-Analog Converter) module, which then controls the motor speed.

**Ideal behavior:** 50% PWM → 5V output → 50% motor speed

**Reality:** Due to component tolerances and non-linearities in the DAC module, the actual output voltage often doesn't match the expected value.

## The Problem

Without calibration, there is a mismatch between the desired output and the actual output:

| PWM % | Expected Voltage | Actual Voltage (example) |
|-------|------------------|--------------------------|
| 0%    | 0.0V             | 0.0V                     |
| 25%   | 2.5V             | 2.1V                     |
| 50%   | 5.0V             | 4.3V                     |
| 75%   | 7.5V             | 6.8V                     |
| 90%   | 9.0V             | 10.2V                    |
| 100%  | 10.0V            | 10.5V                    |

This non-linearity causes:
- **Inaccurate motor speed control** - setting 50% doesn't result in 50% speed
- **Different behavior between GPIO18 and GPIO19** - each DAC channel may have different characteristics
- **Difficulty in fine-tuning ventilation** - small adjustments don't produce expected results

## Solution: Calibration Tables

Each GPIO has its own calibration table that maps PWM duty cycle to measured output voltage. The HrvCalculator uses this table to find the correct PWM value for any desired output.

### How It Works

1. **User measures actual voltages** at various PWM levels using a multimeter
2. **Calibration table is stored** as `pwm:voltage` pairs: `0:0,25:2.1,50:4.3,75:6.8,90:10.2`
3. **HrvCalculator interpolates** - when 5V is needed, it finds PWM between 50% and 75%
4. **Result:** Accurate voltage output regardless of hardware variations

### Calibration Flow

```
Desired Power (50%)
       ↓
Target Voltage (5.0V)
       ↓
Lookup in Calibration Table
       ↓
Interpolate PWM Value (~58%)
       ↓
Send to GPIO via MQTT
       ↓
Actual Output: 5.0V ✓
```

## TEST Mode

When calibrating, you need to measure the raw PWM-to-voltage relationship without any corrections. The **TEST** source mode bypasses calibration:

- **TEST mode:** PWM value is sent directly to GPIO (linear, no calibration)
- **Other modes (POWER, INTAKE, EXHAUST):** Calibration is applied

This allows you to:
1. Set GPIO source to TEST
2. Send specific PWM values
3. Measure actual output voltage
4. Record the measurements in the calibration table
5. Switch back to normal mode with calibration active

## Calibration UI (pwm-settings.html)

Access the calibration UI at: `http://<openhab-host>:8080/static/pwm-settings.html`

### UI Components

#### GPIO Configuration Cards (GPIO 18 & GPIO 19)

Each GPIO has:
- **Source selector** - Choose what value drives this GPIO:
  - `POWER` - Base HRV power output
  - `INTAKE` - Intake motor power (with ratio adjustment)
  - `EXHAUST` - Exhaust motor power (with ratio adjustment)
  - `TEST` - Test slider value (for calibration, no calibration applied)
  - `OFF` - Disable output (0%)

- **Calibration Table** - List of PWM % → Voltage measurements
  - Add rows with "+ Add Row" button
  - Each row has PWM input, voltage input, test button, delete button
  - Green ▶ button sends the PWM value to test

- **Save button** - Stores calibration table and source selection

#### Test Output Section

- **Test Power slider** - Sets the test PWM value (0-100%)
- Only active when GPIO source is set to TEST

### Calibration Procedure

1. **Prepare equipment:**
   - Multimeter set to DC voltage
   - Connect multimeter probes to GPIO output terminals

2. **Set GPIO to TEST mode:**
   - Select "Test" in the Source dropdown
   - Click Save

3. **Add calibration points:**
   - Click "+ Add Row"
   - Enter PWM value (e.g., 0, 25, 50, 75, 100)
   - Click green ▶ button to apply the PWM value
   - Wait for voltage to stabilize
   - Read voltage from multimeter
   - Enter measured voltage in the Voltage field

4. **Recommended calibration points:**
   - 0% (should be ~0V)
   - 10%, 20%, 30%, ... 90% (at least 5-6 points)
   - 100% (should be ~10V, may be higher)

5. **Save and switch source:**
   - Click Save to store calibration
   - Change Source back to INTAKE/EXHAUST/POWER
   - Click Save again

6. **Verify calibration:**
   - Set a known power level (e.g., 50%)
   - Measure output voltage
   - Should match expected value (5V for 50%)

### Example Calibration Table

```
0:0,10:0.95,20:2.1,30:3.2,40:4.1,50:5.05,60:6.1,70:7.2,80:8.3,90:9.4,100:10.18
```

## Technical Details

### Where Calibration Is Applied

Calibration is handled in `HrvCalculator.java`:

```
HrvCalculator.calculate()
    ↓
calculateGpioPwm(source, power, intake, exhaust, test, calibration)
    ↓
if (source == TEST) → return value directly (no calibration)
    ↓
applyCalibration(targetPercent, calibrationJson)
    ↓
pwmForVoltage(targetVoltage, calibration) → linear interpolation
    ↓
return calibrated PWM value
```

### Calibration Table Format

- Comma-separated `pwm:voltage` pairs: `0:0,50:5,100:10`
- PWM values: 0-100 (duty cycle percentage)
- Voltage values: 0-20 (measured voltage in volts)
- At least 2 points required for interpolation
- More points = more accurate calibration

### Linear Interpolation

When the target voltage falls between two calibration points, linear interpolation is used:

```
target = 5.5V
lower point: 50% → 5.0V
upper point: 60% → 6.1V

ratio = (5.5 - 5.0) / (6.1 - 5.0) = 0.45
pwm = 50 + 0.45 * (60 - 50) = 54.5%
```

## Troubleshooting

### Output voltage doesn't match expected value
- Re-run calibration procedure
- Check if correct source is selected
- Verify calibration table was saved

### TEST mode shows wrong voltage
- This is the raw hardware behavior
- Record these values in calibration table
- After calibration, normal modes will compensate

### Calibration seems to have no effect
- Verify source is not set to TEST
- Check that calibration table has at least 2 points
- Ensure Save button was clicked after changes

### Different GPIOs need different calibration
- This is expected - each DAC channel has unique characteristics
- Calibrate each GPIO independently

## Related Files

- `src/main/java/io/github/fiserro/homehab/hrv/HrvCalculator.java` - Calibration logic
- `src/main/java/io/github/fiserro/homehab/module/HrvModule.java` - GPIO items definition
- `openhab-dev/conf/html/pwm-settings.html` - Calibration UI
- `openhab-dev/conf/things/hrv-bridge.things` - MQTT channel definition
