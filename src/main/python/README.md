# HRV Bridge

Simple MQTT to PWM bridge for HRV power output control on Raspberry Pi.

## Overview

This service is a **simple pass-through bridge** that receives PWM values from OpenHAB and sets them directly on GPIO pins. It does NOT perform any calculation or calibration - that is done in OpenHAB (`HrvCalculator.java`).

- **Input**: 0-100 (PWM duty cycle from OpenHAB, already calibrated)
- **Output**: PWM signal on GPIO 18 and GPIO 19

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
| `homehab/hrv/pwm/gpio18` | Subscribe | PWM duty cycle for GPIO 18 (0-100) |
| `homehab/hrv/pwm/gpio19` | Subscribe | PWM duty cycle for GPIO 19 (0-100) |

**Note:** OpenHAB calculates the final PWM values (including source selection and calibration) and publishes them to these topics. The bridge simply sets the received values on the GPIO pins.

## Deployment

```bash
cd src/main/python
./deploy-dac-bridge.sh robertfiser@openhab.home
```

## Configuration

Edit `/etc/systemd/system/dac-bridge.service` on Raspberry Pi:

```
ExecStart=/usr/local/bin/hrv-bridge --mqtt-host localhost
```

## Command Line Options

```
hrv-bridge [OPTIONS]

Options:
  -H, --mqtt-host HOST     MQTT broker host (default: localhost)
  -p, --mqtt-port PORT     MQTT broker port (default: 1883)
  -t, --topic-prefix PRE   MQTT topic prefix (default: homehab/hrv)
  -c, --client-id ID       MQTT client ID (default: hrv-bridge)
  --gpio18 PIN             GPIO pin for channel 18 (default: 18)
  --gpio19 PIN             GPIO pin for channel 19 (default: 19)
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

**Calibration is handled by OpenHAB**, not by this bridge. The bridge simply passes through the PWM values it receives.

For calibration instructions, see:
- `docs/PWM-CALIBRATION.md` - Full calibration guide
- `openhab-dev/conf/html/pwm-settings.html` - Calibration UI

## Testing

```bash
# Set GPIO18 PWM to 50%
mosquitto_pub -h openhab.home -t 'homehab/hrv/pwm/gpio18' -m '50'

# Set GPIO19 PWM to 75%
mosquitto_pub -h openhab.home -t 'homehab/hrv/pwm/gpio19' -m '75'

# Monitor all HRV topics
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
