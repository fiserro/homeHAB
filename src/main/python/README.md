# HRV Bridge

MQTT to PWM/DAC bridge for HRV power output control on Raspberry Pi.

## Overview

This service subscribes to MQTT messages and controls HRV output:
- **Input**: 0-100 (percentage from OpenHAB `hrvOutputPower`)
- **Output**: PWM signal (default) or DAC voltage

## Output Modes

### PWM Mode (default)

```
Raspberry Pi GPIO 18 (PWM)
         ↓
    PWM-to-0-10V module
         ↓
    0-10V analog output
         ↓
    HRV unit
```

**Hardware required:**
- PWM-to-0-10V converter module
- Connect GPIO 18 to PWM input of the module

**PWM module requirements:**
- Input: PWM signal (2 kHz, 3.3V-5V level)
- Output: 0-10V analog

### DAC Mode

```
Raspberry Pi SPI
         ↓
    Waveshare AD/DA (DAC8532)
         ↓
    0-5V analog output
```

**Hardware required:**
- Waveshare High-Precision AD/DA Board
- Connect 5V to VREF for full 0-5V range

## MQTT Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `homehab/hrv/power/set` | Subscribe | Set power level (0-100) |
| `homehab/hrv/power/state` | Publish | Current power level |

## Deployment

```bash
cd src/main/python
./deploy-dac-bridge.sh robertfiser@openhab.home
```

## Configuration

Edit `/etc/systemd/system/dac-bridge.service` on Raspberry Pi:

### PWM Mode (default)
```
ExecStart=/usr/local/bin/hrv-bridge --mqtt-host localhost --mode pwm --pwm-pin 18
```

### DAC Mode
```
ExecStart=/usr/local/bin/hrv-bridge --mqtt-host localhost --mode dac
```

## Command Line Options

```
hrv-bridge [OPTIONS]

Options:
  -H, --mqtt-host HOST     MQTT broker host (default: localhost)
  -p, --mqtt-port PORT     MQTT broker port (default: 1883)
  -t, --topic-prefix PRE   MQTT topic prefix (default: homehab/hrv)
  -m, --mode {pwm,dac}     Output mode (default: pwm)
  --pwm-pin PIN            GPIO pin for PWM (default: 18)
  --pwm-freq FREQ          PWM frequency in Hz (default: 2000)
  -v, --verbose            Enable verbose logging
```

## Hardware PWM Pins

Raspberry Pi hardware PWM capable pins:
- GPIO 12, 13, 18, 19

Default is GPIO 18.

## HRV Physical Wiring (PWM Mode)

Connect Raspberry Pi to HRV unit via PWM-to-Voltage converter module:

```
 RASPBERRY PI 5              PWM MODULE                    HRV UNIT
 ══════════════              ══════════                    ════════

                             ┌─────────┐
 GPIO 18 (PWM) ─────────────►│ PWM  Vo │─────────────────► 0-10V +
 GND (pin 6) ───────────────►│ GND GND │─────────────────► 0-10V -
                             │     GND │◄────────────────  DC-
                             │     VIN │◄────────────────  DC+ (12-30V)
                             └─────────┘
```

### Wiring Table

| # | From | To | Description |
|---|------|-----|-------------|
| 1 | RPi GPIO 18 | PWM: PWM | PWM signal (2 kHz) |
| 2 | RPi GND (pin 6) | PWM: GND (left) | Signal ground |
| 3 | HRV DC+ (12-30V) | PWM: VIN | Power supply |
| 4 | HRV DC- | PWM: GND (right, top) | Power ground |
| 5 | PWM: Vo | HRV: 0-10V + | Output voltage |
| 6 | PWM: GND (right, middle) | HRV: 0-10V - | Output ground |

### Key Points

1. **HRV unit powers the PWM module** - The HRV control unit provides 12-24V DC which powers the PWM-to-Voltage converter.

2. **Common ground** - All ground connections (signal, power, output) must be connected together for proper operation.

3. **Signal flow**:
   - Raspberry Pi generates PWM signal (0-100% duty cycle at 2 kHz)
   - PWM module converts duty cycle to 0-10V analog voltage
   - HRV unit reads 0-10V and adjusts fan speed accordingly

### Notes

- RPi GPIO outputs 3.3V signal. Most PWM modules accept this, but if it doesn't work, you may need a level shifter (3.3V → 5V)
- Total of 5-6 wires depending on whether GND pins are internally connected on the module

## Waveshare AD/DA Board (for DAC mode or future ADC inputs)

The Waveshare library is bundled for:
- **DAC output** (DAC8532) - alternative to PWM mode
- **ADC input** (ADS1256) - for future analog sensors

### Setup for DAC mode

1. Enable SPI: `sudo raspi-config` → Interface Options → SPI → Enable
2. Connect 5V to VREF pin for 0-5V output range
3. Deploy with `--mode dac`

### Future: ADC for analog sensors

The Waveshare board includes ADS1256 (24-bit ADC) for reading analog sensors.
This is not yet implemented but the library is ready.

To restore/update Waveshare library:
```bash
wget -O hp-ad-da.zip https://github.com/waveshareteam/High-Precision-AD-DA-Board/archive/refs/heads/master.zip
unzip -q hp-ad-da.zip
cp High-Precision-AD-DA-Board-master/RaspberryPI/AD-DA/python/DAC8532.py dac_bridge/waveshare_dac8532.py
cp High-Precision-AD-DA-Board-master/RaspberryPI/AD-DA/python/config.py dac_bridge/waveshare_config.py
# Update imports in waveshare_dac8532.py:
# from . import waveshare_config as config
```

## PWM Calibration

PWM modules have non-linear output. The bridge uses a calibration table to correct this.

### Calibration file

`dac_bridge/pwm_calibration.py` contains:

```python
PWM_CALIBRATION = {
    0: 0.0,      # at 0% PWM, measured 0.0V
    5: 0.37,     # at 5% PWM, measured 0.37V
    10: 1.48,    # at 10% PWM, measured 1.48V
    ...
    100: 10.19,  # at 100% PWM, measured 10.19V
}

PWM_CALIBRATION_LINEAR = {
    0: 0.0,
    100: 10.0,
}

ACTIVE_CALIBRATION = PWM_CALIBRATION  # switch for measuring
```

### How to calibrate

1. **Switch to linear mode** for raw PWM output:
   ```python
   # ACTIVE_CALIBRATION = PWM_CALIBRATION
   ACTIVE_CALIBRATION = PWM_CALIBRATION_LINEAR
   ```

2. **Deploy:**
   ```bash
   ./deploy-dac-bridge.sh robertfiser@openhab.home
   ```

3. **Measure:** Set OpenHAB `hrvOutputPower` to 0%, 5%, 10%, ... 100% and record voltages

4. **Update calibration table** with measured values

5. **Switch back to calibrated mode:**
   ```python
   ACTIVE_CALIBRATION = PWM_CALIBRATION
   # ACTIVE_CALIBRATION = PWM_CALIBRATION_LINEAR
   ```

6. **Deploy again** and verify output matches expected values

### When to recalibrate

- After changing PWM module
- After changing power supply voltage (e.g., 24V → 12V)
- If output doesn't match expected values

## Testing

```bash
# Set power to 50%
mosquitto_pub -h openhab.home -t 'homehab/hrv/power/set' -m '50'

# Monitor state
mosquitto_sub -h openhab.home -t 'homehab/hrv/#' -v

# Check service logs
ssh openhab.home 'sudo journalctl -u dac-bridge -f'
```

## Service Management

```bash
ssh openhab.home 'sudo systemctl status dac-bridge'
ssh openhab.home 'sudo systemctl restart dac-bridge'
ssh openhab.home 'sudo journalctl -u dac-bridge -f'
```

## License

MIT. Waveshare library files (`waveshare_*.py`) are also MIT licensed.
