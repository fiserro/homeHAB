# ESP32-C3 SCT013 Current Sensor

Standalone ESP32-C3 firmware for reading AC current via SCT013 current transformers.
Publishes power readings (Watts) to OpenHAB via MQTT.

Based on [Home Energy Monitor with ESP32 & CT Sensor](https://simplyexplained.com/blog/Home-Energy-Monitor-ESP32-CT-Sensor-Emonlib/) with custom measurement algorithm ported from `src/main/python/hrv_bridge/current_sensor.py`.

## Hardware

### Components
- ESP32-C3 DevKit (e.g., ESP32-C3-DevKitM-1)
- SCT013-005 current transformer (5A/1V)
- 2x 120kΩ resistors (voltage divider for bias)
- 10µF capacitor (bias stabilization)
- 3.5mm jack breakout (for SCT013 connector)

### Wiring

Each SCT013 channel requires a bias circuit to center the AC signal
within the ESP32's ADC input range (0-2.5V with 11dB attenuation).

See the [reference schematic](https://simplyexplained.com/blog/Home-Energy-Monitor-ESP32-CT-Sensor-Emonlib/) for the bias circuit diagram.

**Summary:**
- Voltage divider (2x 120kΩ) between 3.3V and GND creates 1.65V DC bias
- SCT013 RED wire connects to the midpoint (same node as GPIO)
- SCT013 BLACK wire connects to GND
- 10µF capacitor between midpoint and GND stabilizes the bias
- GPIO (ADC pin) connects to the midpoint

For multiple channels, duplicate the entire bias circuit per channel.

### Default Pin Mapping

| GPIO | Function        |
|------|-----------------|
| GPIO0 | ADC Channel 0 (SCT013 #1) |

ESP32-C3 ADC1 supports GPIO0-GPIO4. ADC2 is unavailable when WiFi is active.
Configure pins in `src/config.h` (`ADC_PINS` array).

## Build & Flash

### Prerequisites
- [PlatformIO](https://platformio.org/install) (CLI or VS Code extension)

### Configuration

1. Create `src/secrets.h` with WiFi credentials:
```cpp
#define WIFI_SSID "YourNetwork"
#define WIFI_PASSWORD "YourPassword"
```

Or override via build flags in `platformio.ini`:
```ini
build_flags =
    -DWIFI_SSID=\"YourNetwork\"
    -DWIFI_PASSWORD=\"YourPassword\"
    -DMQTT_BROKER=\"zigbee.home\"
```

2. Adjust `src/config.h` for your setup:
   - `ADC_PINS[]` - GPIO pins with SCT013 connected
   - `SCT013_RATIO` - Sensor model (5.0 for SCT013-005)
   - `MAINS_VOLTAGE` - Your mains voltage (230V EU, 120V US)

### Build
```bash
cd esp32/sct013
pio run
```

### Flash via USB
```bash
pio run -t upload
```

### Flash via OTA (WiFi)
```bash
pio run -e ota -t upload
```

Requires the firmware with OTA support to be already running on the device.

### Serial Monitor
```bash
pio device monitor
```

## MQTT Topics

| Topic | Type | Retained | Description |
|-------|------|----------|-------------|
| `homehab/sct013/power/ch0` | Number (int) | Yes | Power in Watts, channel 0 |
| `homehab/sct013/status` | String | Yes | `online` / `offline` (LWT) |

Values are published every 1 second, only when changed.

## Measurement Algorithm

1. **Sample** ADC 200 times at 10kHz (covers ~1 full 50Hz AC cycle)
2. **Filter** outliers - remove top/bottom 10% of sorted samples
3. **Detect** floating input - if variance below threshold, report 0W
4. **Calculate** DC bias from filtered mean
5. **Calculate** RMS voltage of AC component
6. **Convert** to power: `P = Vrms * SCT013_RATIO * MAINS_VOLTAGE`
7. **Threshold** - below 5W reports 0W, above 500W reports 0W (disconnected)
8. **Smooth** via EMA (Exponential Moving Average, alpha=0.3)

## References

- [Home Energy Monitor with ESP32, CT Sensor (SCT-013), and EmonLib](https://simplyexplained.com/blog/Home-Energy-Monitor-ESP32-CT-Sensor-Emonlib/) - bias circuit design reference
- [OpenEnergyMonitor - ESP32 + SCT-013](https://community.openenergymonitor.org/t/esp32-sct-013-000/21437) - community discussion
