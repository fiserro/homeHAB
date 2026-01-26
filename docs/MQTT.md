# MQTT Topic Structure

This document describes the MQTT topic structure used by the homeHAB system.

## Overview

```
homehab/
├── hrv/                      # HRV Bridge (Python on RPi)
│   ├── gpio17                # Bypass valve ON/OFF (digital)
│   ├── pwm/                  # Final PWM values (command only)
│   │   ├── gpio18            # PWM duty cycle 0-100
│   │   └── gpio19            # PWM duty cycle 0-100
│   ├── w1/                   # 1-Wire sensors (topic per sensor ID)
│   │   └── 28-xxxxxxxxxxxx   # Temperature from sensor with ID 28-xxxx
│   ├── current/              # SCT013 current sensors (via Waveshare ADC)
│   │   ├── ad0               # Power in Watts from ADC channel 0
│   │   └── ad1               # Power in Watts from ADC channel 1
│   ├── co2                   # CO2 concentration in ppm (MH-Z19C)
│   └── co2_temp              # Temperature from CO2 sensor in °C
├── panel/                    # ESP32 Panel
│   ├── command/              # Commands from panel to OpenHAB
│   │   ├── temporaryManualMode   # ON/OFF
│   │   ├── temporaryBoostMode    # ON/OFF
│   │   └── manualPower           # 0-100
│   └── status                # online/offline (birth/will)
├── state/                    # OpenHAB → Panel (states for display)
│   ├── hrvOutputPower
│   ├── temperature
│   ├── airHumidity
│   ├── co2
│   ├── pressure
│   ├── manualMode
│   ├── temporaryBoostMode
│   ├── temporaryManualMode
│   ├── smoke
│   ├── manualPower
│   └── bypass
└── zigbee2mqtt/              # Zigbee sensors (managed by Z2M - do not modify)
    ├── <device>              # state JSON
    ├── <device>/set          # command
    └── bridge/devices        # device list
```

## HRV Bridge Topics

The HRV Bridge runs on Raspberry Pi and controls GPIO outputs for ventilation.

### GPIO Output Topics (Subscribe - OpenHAB → Bridge)

| Topic | Type | Values | Description |
|-------|------|--------|-------------|
| `homehab/hrv/gpio17` | Switch | ON/OFF | Bypass valve control (digital output) |
| `homehab/hrv/pwm/gpio18` | Number | 0-100 | PWM duty cycle for GPIO18 |
| `homehab/hrv/pwm/gpio19` | Number | 0-100 | PWM duty cycle for GPIO19 |

### Sensor Topics (Publish - Bridge → OpenHAB)

| Topic | Type | Values | Description |
|-------|------|--------|-------------|
| `homehab/hrv/w1/<sensor_id>` | Number | float | Temperature in °C from 1-Wire sensor (retained) |
| `homehab/hrv/current/ad0` | Number | int | Power in Watts from SCT013 on ADC channel 0 (retained) |
| `homehab/hrv/current/ad1` | Number | int | Power in Watts from SCT013 on ADC channel 1 (retained) |
| `homehab/hrv/co2` | Number | int | CO2 concentration in ppm from MH-Z19C (retained) |
| `homehab/hrv/co2_temp` | Number | int | Temperature in °C from MH-Z19C sensor (retained) |

**CO2 Sensor (MH-Z19C):**
- Connected via UART on GPIO14 (TX) and GPIO15 (RX)
- Measurement range: 400-5000 ppm
- Reading interval: 30 seconds
- Wire colors: Red=5V, Black=GND, Green=TX→RPi RX, Blue=RX←RPi TX

**Current Sensors (SCT013):**
- Connected via Waveshare High Precision AD/DA board (ADS1256 ADC)
- AD0 and AD1 channels used for current measurement
- Floating input detection: reports 0W when sensor disconnected
- Reading interval: 200ms sampling, 1s publish (if changed)

**1-Wire Sensors:**
- Each DS18B20 sensor publishes to its own topic using its unique ID
- Example: `homehab/hrv/w1/28-0316840d44ff` for sensor with ID `28-0316840d44ff`
- Multiple sensors can be connected in parallel on GPIO27
- Bridge auto-detects all connected sensors and publishes each one

**Bypass Valve (GPIO17):**
- OFF = valve closed, air flows through heat exchanger (default, winter mode)
- ON = valve open, air bypasses heat exchanger (summer mode)

**PWM Outputs (GPIO18/19):**
OpenHAB calculates the final PWM values (including source selection and calibration)
and sends them directly to the bridge. The Python bridge simply receives these values
and sets them on the GPIO pins. All calculation logic (source mapping, calibration
interpolation) is handled by HrvCalculator in OpenHAB.

### Configuration (OpenHAB Items, not MQTT)

GPIO source selection and calibration tables are stored as OpenHAB items:
- `sourceGpio18` - Source for GPIO18: POWER|INTAKE|EXHAUST|TEST|OFF
- `sourceGpio19` - Source for GPIO19: POWER|INTAKE|EXHAUST|TEST|OFF
- `calibrationTableGpio18` - Calibration table `pwm:voltage,pwm:voltage,...`
- `calibrationTableGpio19` - Calibration table `pwm:voltage,pwm:voltage,...`

## ESP32 Panel Topics

### Commands (Panel → OpenHAB)
- `homehab/panel/command/temporaryManualMode` - ON/OFF
- `homehab/panel/command/temporaryBoostMode` - ON/OFF
- `homehab/panel/command/manualPower` - 0-100

### Status
- `homehab/panel/status` - online/offline (LWT)

## State Topics (OpenHAB → Panel)

These topics publish OpenHAB item states for display on the ESP32 panel:

- `homehab/state/hrvOutputPower`
- `homehab/state/temperature`
- `homehab/state/airHumidity`
- `homehab/state/co2`
- `homehab/state/pressure`
- `homehab/state/manualMode`
- `homehab/state/temporaryBoostMode`
- `homehab/state/temporaryManualMode`
- `homehab/state/smoke`
- `homehab/state/manualPower`
- `homehab/state/bypass`

## Related Files

- `src/main/python/hrv_bridge/__init__.py` - Python HRV bridge (MQTT↔GPIO/sensors)
- `src/main/python/hrv_bridge/co2_sensor.py` - MH-Z19C CO2 sensor module
- `src/main/python/hrv_bridge/current_sensor.py` - SCT013 current sensor module
- `src/main/java/io/github/fiserro/homehab/hrv/HrvCalculator.java` - HRV calculation logic including GPIO calibration
- `src/main/java/io/github/fiserro/homehab/module/HrvModule.java` - HRV module interface with GPIO items
- `openhab-dev/conf/things/hrv-bridge.things` - OpenHAB MQTT thing for HRV
- `openhab-dev/conf/things/panel-mqtt.things` - Panel commands thing
- `openhab-dev/conf/automation/jsr223/PanelMqttBridge.java` - Panel state publishing
- `openhab-dev/conf/html/pwm-settings.html` - PWM calibration UI
- `esp32-panel/hrv-panel.yaml` - ESP32 panel firmware
- `docs/RPI-WIRING.md` - Raspberry Pi GPIO wiring documentation
