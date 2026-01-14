# Raspberry Pi GPIO Wiring

This document describes the GPIO pin connections for the homeHAB HRV control system.

## GPIO Header Pinout

```
                    Raspberry Pi 5 GPIO Header
                    ===========================

        3.3V   (1)  [■ ■]  (2)   5V        ← 5V for relay module
      GPIO2    (3)  [□ □]  (4)   5V
      GPIO3    (5)  [□ □]  (6)   GND
      GPIO4    (7)  [■ □]  (8)   GPIO14    ← 1-Wire (DS18B20)
        GND    (9)  [□ □]  (10)  GPIO15
     GPIO17   (11)  [░ □]  (12)  GPIO18    ← Reserved for Waveshare AD/DA
     GPIO27   (13)  [□ □]  (14)  GND
     GPIO22   (15)  [░ ░]  (16)  GPIO23    ← Reserved for Waveshare AD/DA
        3.3V  (17)  [□ □]  (18)  GPIO24
     GPIO10   (19)  [□ □]  (20)  GND
      GPIO9   (21)  [□ □]  (22)  GPIO25
     GPIO11   (23)  [□ □]  (24)  GPIO8
        GND   (25)  [□ □]  (26)  GPIO7
      GPIO0   (27)  [□ □]  (28)  GPIO1
      GPIO5   (29)  [■ □]  (30)  GND       ← Bypass valve relay
      GPIO6   (31)  [□ ■]  (32)  GPIO12    ← PWM (HW) Fan 1
     GPIO13   (33)  [■ □]  (34)  GND       ← PWM (HW) Fan 2
     GPIO19   (35)  [□ □]  (36)  GPIO16
     GPIO26   (37)  [□ □]  (38)  GPIO20
        GND   (39)  [□ □]  (40)  GPIO21

    Legend: ■ = Used, □ = Available, ░ = Reserved (Waveshare AD/DA)
```

## Pin Assignments

| Pin # | GPIO | Function | Wire Color | Description |
|-------|------|----------|------------|-------------|
| 1 | 3.3V | Power | Red        | Temperature sensor power (DS18B20) |
| 2 | 5V | Power | Red        | Relay module VCC (if 5V relay) |
| 7 | GPIO4 | 1-Wire | Yellow     | Temperature sensor data (DS18B20) |
| 29 | GPIO5 | Digital Out | Green      | Bypass valve relay control |
| 32 | GPIO12 | PWM (HW) | -          | Fan speed control (Intake or Exhaust) |
| 33 | GPIO13 | PWM (HW) | -          | Fan speed control (Intake or Exhaust) |
| 6, 9, 14, 20, 25, 30, 34, 39 | GND | Ground | Black      | Common ground |

### Reserved for Waveshare AD/DA Board

| Pin # | GPIO | Waveshare Function |
|-------|------|-------------------|
| 11 | GPIO17 | DRDY (Data Ready) |
| 12 | GPIO18 | RST (Reset) |
| 15 | GPIO22 | CS (Chip Select ADC) |
| 16 | GPIO23 | CS_DAC (Chip Select DAC) |

## Module Connections

### 1. HRV Fan Speed Control (PWM)

Two hardware PWM outputs control the HRV fan speeds via PWM-to-0-10V converter modules.

| GPIO | OpenHAB Item | MQTT Topic | Description |
|------|--------------|------------|-------------|
| GPIO12 | `hrvOutputGpio18` | `homehab/hrv/pwm/gpio18` | Configurable: Intake or Exhaust |
| GPIO13 | `hrvOutputGpio19` | `homehab/hrv/pwm/gpio19` | Configurable: Intake or Exhaust |

**Note:** MQTT topics still use `gpio18`/`gpio19` names for backwards compatibility, but actual pins are GPIO12/GPIO13.

**Wiring:**
```
RPi GPIO12/13  ────►  PWM Module (PWM in)
RPi GND        ────►  PWM Module (GND)
PWM Module Vo  ────►  HRV Unit (0-10V+)
PWM Module GND ────►  HRV Unit (0-10V-)
HRV Unit DC+   ────►  PWM Module (VIN, 12-24V)
HRV Unit DC-   ────►  PWM Module (GND)
```

### 2. Bypass Valve Control (Digital)

Digital output controls a relay that switches the bypass valve.

| GPIO | OpenHAB Item | MQTT Topic | Description |
|------|--------------|------------|-------------|
| GPIO5 | `bypass` | `homehab/hrv/gpio17` | OFF = valve closed (through exchanger), ON = valve open (bypass) |

**Note:** MQTT topic still uses `gpio17` name for backwards compatibility, but actual pin is GPIO5.

**Wiring (3-pin relay module: +, -, S):**
```
RPi GPIO5 (Pin 29)  ────►  Relay Module S (Signal)
RPi 5V (Pin 2)      ────►  Relay Module + (VCC)
RPi GND             ────►  Relay Module - (GND)
Relay NO            ────►  Bypass Valve
Relay COM           ────►  Power Supply
```

**Note:** Most 3-pin relay modules work with 5V on VCC pin. If the relay doesn't switch reliably, verify the module's voltage requirements.

### 3. Temperature Sensor (DS18B20)

1-Wire temperature sensor for ambient temperature measurement. GPIO4 is the default 1-Wire pin on Raspberry Pi.

| GPIO | Wire | Description |
|------|------|-------------|
| Pin 1 (3.3V) | Red | Power supply |
| GPIO4 (Pin 7) | Yellow | Data (1-Wire) |
| GND | Black | Ground |

**Wiring:**
```
RPi 3.3V (Pin 1)   ────►  DS18B20 VDD (Red)
RPi GPIO4 (Pin 7)  ────►  DS18B20 DQ (Yellow)
RPi GND            ────►  DS18B20 GND (Black)

         Red (VDD)
            │
           ┌┴┐
           │ │ 4.7kΩ
           └┬┘
            │
         Yellow (DQ)
```

**Note:** The 4.7kΩ pull-up resistor connects the red (VDD) and yellow (DQ) wires. This resistor is required for stable 1-Wire communication.

**Adding multiple sensors:**

Additional DS18B20 sensors can be connected in parallel to the same 1-Wire bus. All sensors share the same three wires (VDD, DQ, GND). Each sensor has a unique 64-bit address, allowing individual identification.

```
RPi 3.3V ────┬────► Sensor 1 VDD ────► Sensor 2 VDD ────► Sensor N VDD
             │
            ┌┴┐
            │ │ 4.7kΩ (single resistor for all sensors)
            └┬┘
             │
RPi GPIO4 ───┴────► Sensor 1 DQ  ────► Sensor 2 DQ  ────► Sensor N DQ

RPi GND ──────────► Sensor 1 GND ────► Sensor 2 GND ────► Sensor N GND
```

**Enable 1-Wire interface:**
```bash
# Add to /boot/config.txt (GPIO4 is the default):
dtoverlay=w1-gpio

# Or explicitly specify the pin:
dtoverlay=w1-gpio,gpiopin=4

# Reboot and check:
ls /sys/bus/w1/devices/
```

## Physical Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     RASPBERRY PI 5                          │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  GPIO Header                                        │    │
│  │  ┌──┬──┐                                            │    │
│  │  │1 │2 │  ← Pin 1: 3.3V (DS18B20), Pin 2: 5V (Relay)│    │
│  │  ├──┼──┤                                            │    │
│  │  │  │  │                                            │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │6 │  ← Pin 6: GND                              │    │
│  │  ├──┼──┤                                            │    │
│  │  │7 │  │  ← Pin 7: GPIO4 (1-Wire DS18B20 data)      │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │  │  ░ Pin 11-12, 15-16: Reserved (Waveshare)  │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │  │                                            │    │
│  │  │ ····│                                            │    │
│  │  ├──┼──┤                                            │    │
│  │  │29│  │  ← Pin 29: GPIO5 (Bypass relay)            │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │32│  ← Pin 32: GPIO12 (PWM Fan 1)              │    │
│  │  ├──┼──┤                                            │    │
│  │  │33│  │  ← Pin 33: GPIO13 (PWM Fan 2)              │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │  │                                            │    │
│  │  └──┴──┘                                            │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│   USB-C   USB   USB   ETH   HDMI  HDMI                      │
└─────────────────────────────────────────────────────────────┘
```

## Summary Table

| Component | GPIO | Pin # | Direction | Signal Type |
|-----------|------|-------|-----------|-------------|
| Temp Sensor | GPIO4 | 7 | Input | 1-Wire |
| Bypass Valve | GPIO5 | 29 | Output | Digital (0/1) |
| PWM Fan 1 | GPIO12 | 32 | Output | PWM 2kHz (HW) |
| PWM Fan 2 | GPIO13 | 33 | Output | PWM 2kHz (HW) |

### Reserved (Waveshare AD/DA)

| Function | GPIO | Pin # |
|----------|------|-------|
| DRDY | GPIO17 | 11 |
| RST | GPIO18 | 12 |
| CS (ADC) | GPIO22 | 15 |
| CS (DAC) | GPIO23 | 16 |

## Related Documentation

- [MQTT Topics](MQTT.md) - MQTT topic structure for HRV control
- [PWM Calibration](PWM-CALIBRATION.md) - Calibrating PWM to voltage output
- [Python HRV Bridge](../src/main/python/README.md) - Python service documentation
