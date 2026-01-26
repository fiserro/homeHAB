# homeHAB

OpenHAB automation library for home automation with focus on HRV (Heat Recovery Ventilator) control system.

## Overview

homeHAB provides a Java-based automation framework for OpenHAB with:
- **HRV Control System** - Automatic ventilation control based on CO2, humidity, and other sensors
- **Zigbee Integration** - Support for Zigbee sensors via Zigbee2MQTT
- **DAC Output** - Analog voltage output (0-5V) for HRV power control via Waveshare AD/DA board
- **Code Generation** - Automatic generation of OpenHAB items, things, and UI from Java annotations

## Architecture

### Development Environment

```
┌─────────────────────────────────────────────────────────────┐
│                    Raspberry Pi (openhab.home)              │
│                                                             │
│  ┌──────────────┐     ┌───────────────┐     ┌─────────────┐ │
│  │ HRV Bridge   │────▶│   Mosquitto   │◀────│ Zigbee2MQTT │ │
│  │ (Python)     │     │ (MQTT Broker) │     │             │ │
│  └──────────────┘     └───────────────┘     └─────────────┘ │
│         │                    ▲                     │        │
│         ▼                    │                     ▼        │
│  ┌──────────────┐            │              ┌───────────┐   │
│  │   DAC8532    │            │              │  Zigbee   │   │
│  │  (hardware)  │            │              │  sensors  │   │
│  └──────────────┘            │              └───────────┘   │
└──────────────────────────────┼──────────────────────────────┘
                               │
                               │ MQTT (port 1883)
                               │
┌──────────────────────────────┼──────────────────────────────┐
│           Dev Machine (Docker)                              │
│                              ▼                              │
│                     ┌───────────────┐                       │
│                     │   OpenHAB     │                       │
│                     │   (Docker)    │                       │
│                     └───────────────┘                       │
│                              │                              │
│                     http://localhost:8888                   │
└─────────────────────────────────────────────────────────────┘
```

### Production Environment

```
┌─────────────────────────────────────────────────────────────┐
│                    Raspberry Pi (openhab.home)              │
│                                                             │
│  ┌──────────────┐     ┌───────────────┐     ┌─────────────┐ │
│  │ HRV Bridge   │────▶│   Mosquitto   │◀────│ Zigbee2MQTT │ │
│  │ (Python)     │     │ (MQTT Broker) │     │             │ │
│  └──────────────┘     └───────────────┘     └─────────────┘ │
│         │                    ▲                     │        │
│         ▼                    │                     ▼        │
│  ┌──────────────┐            │              ┌───────────┐   │
│  │   DAC8532    │      ┌─────┴─────┐        │  Zigbee   │   │
│  │  (hardware)  │      │  OpenHAB  │        │  sensors  │   │
│  └──────────────┘      │ (native)  │        └───────────┘   │
│                        └───────────┘                        │
│                              │                              │
│                     http://openhab.home:8080                │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
homeHAB/
├── src/main/java/              # Java automation library
│   └── io/github/fiserro/homehab/
│       ├── HabState.java       # Central state record with annotations
│       ├── hrv/                # HRV control logic
│       └── generator/          # Code generators for OpenHAB config
├── src/main/python/            # HRV Bridge service
│   └── hrv_bridge/             # MQTT to DAC8532 bridge
├── openhab-dev/                # OpenHAB dev configuration
│   └── conf/
│       ├── automation/         # Java rules and libraries
│       ├── items/              # Generated items
│       ├── things/             # MQTT things configuration
│       └── sitemaps/           # UI sitemaps
├── docker-compose.yml          # Dev environment
└── deploy.sh                   # Deployment script
```

## Quick Start

### Prerequisites

- Java JDK 21
- Maven 3.x
- Docker & Docker Compose
- Raspberry Pi with:
  - Mosquitto MQTT broker
  - Zigbee2MQTT
  - Waveshare High-Precision AD/DA Board (for DAC output)

### Development

```bash
# Start OpenHAB in Docker
docker-compose up -d

# Build and deploy Java library
./deploy.sh dev

# Deploy HRV Bridge to Raspberry Pi
cd src/main/python
./deploy-hrv-bridge.sh
```

### Generate Configuration

```bash
# Generate items from HabState.java annotations
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--habStateEnabled=true --mqttEnabled=false"
```

## MQTT Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `zigbee2mqtt/<device>` | Sensors → OpenHAB | Zigbee sensor data |
| `homehab/dac/power/set` | OpenHAB → DAC | HRV power level (0-100) |
| `homehab/dac/power/state` | DAC → OpenHAB | Current power level |
| `homehab/dac/voltage/state` | DAC → OpenHAB | Current voltage (0-5V) |

## HRV Control Logic

The HRV calculator uses priority-based decision tree:

1. **Safety** - Smoke detector, window open → OFF
2. **Manual modes** - Manual, temporary manual → configured power
3. **Boost modes** - Boost, temporary boost → HIGH power
4. **Automatic** - Based on humidity/CO2 thresholds → LOW/MID/HIGH

## Documentation

### Project Guides
- [CLAUDE.md](CLAUDE.md) - Detailed development guide and API reference
- [docs/INSTALLATION.md](docs/INSTALLATION.md) - Infrastructure installation (Raspberry Pi, MQTT, Zigbee2MQTT)
- [docs/MAIN-UI-PAGES.md](docs/MAIN-UI-PAGES.md) - OpenHAB Main UI pages guide

### Component Documentation
- [esp32-panel/README.md](esp32-panel/README.md) - ESP32 touch panel (ESPHome, LVGL)
- [src/main/python/README.md](src/main/python/README.md) - HRV Bridge (MQTT to PWM/DAC)
- [openhab-dev/README.md](openhab-dev/README.md) - Docker development environment

## License

MIT
