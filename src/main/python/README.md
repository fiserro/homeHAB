# HRV Bridge

Simple MQTT to GPIO bridge for HRV control on Raspberry Pi.

## Overview

This service is a **simple pass-through bridge** that receives commands from OpenHAB and sets them directly on GPIO pins. It does NOT perform any calculation or calibration - that is done in OpenHAB (`HrvCalculator.java`).

**GPIO Outputs:**
| GPIO | Type | Input | Description |
|------|------|-------|-------------|
| GPIO 5 | Digital | ON/OFF | Bypass valve control |
| GPIO 12 | PWM (HW) | 0-100 | Fan speed (intake or exhaust) |
| GPIO 13 | PWM (HW) | 0-100 | Fan speed (intake or exhaust) |

**Note:** GPIO 17, 18, 22, 23 are reserved for Waveshare AD/DA board.

**GPIO Inputs (1-Wire):**
| GPIO | Type | Output | Description |
|------|------|--------|-------------|
| GPIO 4 | 1-Wire | Temperature (°C) | DS18B20 temperature sensors (default 1-Wire bus) |

**ADC Inputs (Waveshare AD/DA - ADS1256):**
| Channel | Type | Output | Description |
|---------|------|--------|-------------|
| AD0 | Analog | Power (W) | SCT013 current sensor #1 |
| AD1 | Analog | Power (W) | SCT013 current sensor #2 |

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

**Subscribe (OpenHAB → Bridge):**
| Topic | Values | Description |
|-------|--------|-------------|
| `homehab/hrv/gpio17` | ON/OFF | Bypass valve (digital output) |
| `homehab/hrv/pwm/gpio18` | 0-100 | PWM duty cycle for GPIO 18 |
| `homehab/hrv/pwm/gpio19` | 0-100 | PWM duty cycle for GPIO 19 |

**Publish (Bridge → OpenHAB):**
| Topic | Values | Description |
|-------|--------|-------------|
| `homehab/hrv/w1/<sensor_id>` | float | Temperature in °C from 1-Wire sensor (retained) |
| `homehab/hrv/current/ad<n>` | int | Power in Watts from SCT013 sensor (retained) |

Each DS18B20 sensor publishes to its own topic using its unique ID (e.g., `homehab/hrv/w1/28-0316840d44ff`). Multiple sensors are auto-detected and published.

Each SCT013 current sensor publishes to its own topic based on ADC channel (e.g., `homehab/hrv/current/ad0`, `homehab/hrv/current/ad1`). Values are in Watts, calculated from measured current × 230V.

**Note:** OpenHAB calculates the final values (including source selection and calibration) and publishes them to these topics. The bridge simply sets the received values on the GPIO pins.

## Deployment

```bash
cd src/main/python
./deploy-hrv-bridge.sh robertfiser@openhab.home
```

## Configuration

Edit `/etc/systemd/system/hrv-bridge.service` on Raspberry Pi:

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
  --gpio17 PIN             GPIO pin for bypass valve (default: 5)
  --gpio18 PIN             GPIO pin for PWM channel 18 (default: 12)
  --gpio19 PIN             GPIO pin for PWM channel 19 (default: 13)
  --pwm-freq FREQ          PWM frequency in Hz (default: 2000)
  --temp-interval SEC      Temperature reading interval (default: 30)
  --current-channels CH    ADC channels for SCT013 sensors (default: 0,1)
  --no-current             Disable current sensing
  -v, --verbose            Enable verbose logging
```

## Hardware PWM Pins

Raspberry Pi hardware PWM capable pins:
- GPIO 12, 13, 18, 19

Default is GPIO 12 and 13 (GPIO 18 is reserved for Waveshare AD/DA board).

## HRV Physical Wiring (PWM Mode)

Connect Raspberry Pi to HRV unit via PWM-to-Voltage converter module:

```
 RASPBERRY PI 5              PWM MODULE                    HRV UNIT
 ══════════════              ══════════                    ════════

                             ┌─────────┐
 GPIO 12 (PWM) ─────────────►│ PWM  Vo │─────────────────► 0-10V +
 GND (pin 6) ───────────────►│ GND GND │─────────────────► 0-10V -
                             │     GND │◄────────────────  DC-
                             │     VIN │◄────────────────  DC+ (12-30V)
                             └─────────┘
```

### Wiring Table

| # | From | To | Description |
|---|------|-----|-------------|
| 1 | RPi GPIO 12 | PWM: PWM | PWM signal (2 kHz) |
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

### Current Sensing with SCT013

The Waveshare board's ADS1256 (24-bit ADC) is used for reading SCT013 current transformers.

**SCT013-005 Specifications:**
- Input: 0-5A AC
- Output: 0-1V AC (proportional to current)
- Measurable power range: ~5W to ~1150W (at 230V)

**Wiring:**
- SCT output → ADx input (AD0, AD1, etc.)
- SCT GND → AGND on Waveshare board
- No bias circuit needed - SCT013-005 has internal burden resistor

**Measurement process:**
1. Sample voltage at 2000 SPS (samples per second)
2. Filter outliers (remove top/bottom 10% of samples)
3. Remove DC offset (calculated from filtered samples)
4. Calculate RMS of AC component
5. Apply channel calibration factor
6. Convert to power: `Power = Vrms × 5A/V × 230V`

**Noise filtering and spike protection:**

| Parameter | Value | Description |
|-----------|-------|-------------|
| Noise threshold | 3W | Values below this are reported as 0W |
| Max power limit | 500W | Values above this are reported as 0W (disconnected sensor) |
| Spike filter | 10% percentile | Removes outliers from samples |
| EMA smoothing | α=0.3 | Exponential moving average for stable readings |
| Channel calibration | AD0=1.0, AD1=0.85 | Compensates for ADC channel differences |

**MQTT publish timing:**

| Condition | Interval | Description |
|-----------|----------|-------------|
| Sampling | 200ms | Internal sampling rate |
| Value changed | 1s | Publish within 1 second when value changes |
| Value unchanged | 60s | Publish every 60 seconds even if no change |
| 0 → non-zero | instant | Immediate response when device turns on |
| non-zero → 0 | instant | Immediate response when device turns off |

**Instant on/off behavior:**
- When transitioning from 0W to any non-zero value, EMA is reset immediately (no slow ramp-up)
- When value drops below noise threshold (3W), it's immediately reported as 0W
- Large changes (>50W) bypass EMA smoothing for faster response

**Median filtering before MQTT publish:**
- 5 samples are collected over 1 second
- Median value is used for publishing
- This filters out transient spikes from connecting/disconnecting sensors

To restore/update Waveshare library:
```bash
wget -O hp-ad-da.zip https://github.com/waveshareteam/High-Precision-AD-DA-Board/archive/refs/heads/master.zip
unzip -q hp-ad-da.zip
cp High-Precision-AD-DA-Board-master/RaspberryPI/AD-DA/python/DAC8532.py hrv_bridge/waveshare_dac8532.py
cp High-Precision-AD-DA-Board-master/RaspberryPI/AD-DA/python/config.py hrv_bridge/waveshare_config.py
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
# Set bypass valve ON (open, summer mode)
mosquitto_pub -h openhab.home -t 'homehab/hrv/gpio17' -m 'ON'

# Set bypass valve OFF (closed, through heat exchanger)
mosquitto_pub -h openhab.home -t 'homehab/hrv/gpio17' -m 'OFF'

# Set GPIO18 PWM to 50%
mosquitto_pub -h openhab.home -t 'homehab/hrv/pwm/gpio18' -m '50'

# Set GPIO19 PWM to 75%
mosquitto_pub -h openhab.home -t 'homehab/hrv/pwm/gpio19' -m '75'

# Monitor all HRV topics (including temperature)
mosquitto_sub -h openhab.home -t 'homehab/hrv/#' -v

# Read temperature sensor directly on RPi
ssh openhab.home 'cat /sys/bus/w1/devices/28-*/temperature'

# Monitor current readings
mosquitto_sub -h openhab.home -t 'homehab/hrv/current/#' -v

# Check service logs
ssh openhab.home 'sudo journalctl -u hrv-bridge -f'
```

## Service Management

```bash
ssh openhab.home 'sudo systemctl status hrv-bridge'
ssh openhab.home 'sudo systemctl restart hrv-bridge'
ssh openhab.home 'sudo journalctl -u hrv-bridge -f'
```

## License

MIT. Waveshare library files (`waveshare_*.py`) are also MIT licensed.
