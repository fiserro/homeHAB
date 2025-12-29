# Raspberry Pi GPIO Wiring

This document describes the GPIO pin connections for the homeHAB HRV control system.

## GPIO Header Pinout

```
                    Raspberry Pi 5 GPIO Header
                    ===========================

        3.3V   (1)  [■ ■]  (2)   5V
      GPIO2    (3)  [□ □]  (4)   5V
      GPIO3    (5)  [□ □]  (6)   GND
      GPIO4    (7)  [□ □]  (8)   GPIO14
        GND    (9)  [□ □]  (10)  GPIO15
     GPIO17   (11)  [■ □]  (12)  GPIO18  ← PWM (Intake/Exhaust)
     GPIO27   (13)  [■ □]  (14)  GND
     GPIO22   (15)  [□ □]  (16)  GPIO23
        3.3V  (17)  [□ □]  (18)  GPIO24
     GPIO10   (19)  [□ □]  (20)  GND
      GPIO9   (21)  [□ □]  (22)  GPIO25
     GPIO11   (23)  [□ □]  (24)  GPIO8
        GND   (25)  [□ □]  (26)  GPIO7
      GPIO0   (27)  [□ □]  (28)  GPIO1
      GPIO5   (29)  [□ □]  (30)  GND
      GPIO6   (31)  [□ □]  (32)  GPIO12
     GPIO13   (33)  [□ ■]  (34)  GND
     GPIO19   (35)  [■ □]  (36)  GPIO16  ← PWM (Intake/Exhaust)
     GPIO26   (37)  [□ □]  (38)  GPIO20
        GND   (39)  [□ □]  (40)  GPIO21

    Legend: ■ = Used pin, □ = Available
```

## Pin Assignments

| Pin # | GPIO | Function | Wire Color | Description |
|-------|------|----------|------------|-------------|
| 1 | 3.3V | Power | Red | Temperature sensor power (DS18B20) |
| 11 | GPIO17 | Digital Out | - | Bypass valve relay control |
| 12 | GPIO18 | PWM | - | Fan speed control (Intake or Exhaust) |
| 13 | GPIO27 | 1-Wire | Yellow | Temperature sensor data (DS18B20) |
| 35 | GPIO19 | PWM | - | Fan speed control (Intake or Exhaust) |
| 6, 9, 14, 20, 25, 30, 34, 39 | GND | Ground | Black | Common ground |

## Module Connections

### 1. HRV Fan Speed Control (PWM)

Two PWM outputs control the HRV fan speeds via PWM-to-0-10V converter modules.

| GPIO | OpenHAB Item | MQTT Topic | Description |
|------|--------------|------------|-------------|
| GPIO18 | `hrvOutputGpio18` | `homehab/hrv/pwm/gpio18` | Configurable: Intake or Exhaust |
| GPIO19 | `hrvOutputGpio19` | `homehab/hrv/pwm/gpio19` | Configurable: Intake or Exhaust |

**Wiring:**
```
RPi GPIO18/19  ────►  PWM Module (PWM in)
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
| GPIO17 | `bypass` | `homehab/hrv/gpio17` | OFF = valve closed (through exchanger), ON = valve open (bypass) |

**Wiring:**
```
RPi GPIO17  ────►  Relay Module (Signal/IN)
RPi 3.3V    ────►  Relay Module (VCC) *or 5V depending on relay
RPi GND     ────►  Relay Module (GND)
Relay NO    ────►  Bypass Valve
Relay COM   ────►  Power Supply
```

### 3. Temperature Sensor (DS18B20)

1-Wire temperature sensor for ambient temperature measurement.

| GPIO | Wire | Description |
|------|------|-------------|
| Pin 1 (3.3V) | Red | Power supply |
| GPIO27 (Pin 13) | Yellow | Data (1-Wire) |
| GND | Black | Ground |

**Wiring:**
```
RPi 3.3V (Pin 1)    ────►  DS18B20 VDD (Red)
RPi GPIO27 (Pin 13) ────►  DS18B20 DQ (Yellow)
RPi GND             ────►  DS18B20 GND (Black)

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
RPi GPIO27 ──┴────► Sensor 1 DQ  ────► Sensor 2 DQ  ────► Sensor N DQ

RPi GND ──────────► Sensor 1 GND ────► Sensor 2 GND ────► Sensor N GND
```

**Enable 1-Wire interface:**
```bash
# Add to /boot/config.txt:
dtoverlay=w1-gpio,gpiopin=27

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
│  │  │1 │2 │  ← Pin 1: 3.3V (Red wire to DS18B20)       │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │  │                                            │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │6 │  ← Pin 6: GND                              │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │  │                                            │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │  │                                            │    │
│  │  ├──┼──┤                                            │    │
│  │  │11│12│  ← Pin 11: GPIO17 (Bypass relay)           │    │
│  │  ├──┼──┤    Pin 12: GPIO18 (PWM to fan)             │    │
│  │  │13│  │  ← Pin 13: GPIO27 (DS18B20 data)           │    │
│  │  ├──┼──┤                                            │    │
│  │  │  │  │                                            │    │
│  │  │ ····│                                            │    │
│  │  ├──┼──┤                                            │    │
│  │  │35│  │  ← Pin 35: GPIO19 (PWM to fan)             │    │
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
| PWM Fan 1 | GPIO18 | 12 | Output | PWM 2kHz |
| PWM Fan 2 | GPIO19 | 35 | Output | PWM 2kHz |
| Bypass Valve | GPIO17 | 11 | Output | Digital (0/1) |
| Temp Sensor | GPIO27 | 13 | Input | 1-Wire |

## Related Documentation

- [MQTT Topics](MQTT.md) - MQTT topic structure for HRV control
- [PWM Calibration](PWM-CALIBRATION.md) - Calibrating PWM to voltage output
- [Python HRV Bridge](../src/main/python/README.md) - Python service documentation
