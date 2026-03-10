# HRV Future Extensions

Possible extensions to the HRV control system, inspired by professional recuperation units.

## Current Features

- CO2-based ventilation control (3 thresholds: low/mid/high)
- Humidity threshold control
- Smoke detection (emergency shutdown) and gas detection (max ventilation)
- Bypass valve (summer heat exchanger bypass)
- Intake/exhaust ratio (pressure balancing)
- 4x duct temperature sensors (DS18B20: outdoor, supply, extract, exhaust)
- PWM calibration tables per GPIO
- Filter cleaning management (interval + timestamp)
- Boost / manual / temporary manual modes
- Power measurement (SCT013 via Waveshare ADC)

## Planned Extensions

### 1. Heat Recovery Efficiency Calculation

**Effort:** Software only (no hardware needed)

Calculate real-time heat recovery efficiency from existing 4 duct temperatures:

```
η = (supplyAir - outdoorAir) / (extractAir - outdoorAir) × 100%
```

Useful for:
- Monitoring heat exchanger degradation over time
- Detecting clogged filters (efficiency drops)
- Dashboard visualization and long-term trends in Grafana

### 2. Anti-Freeze Protection

**Effort:** Software only (no hardware needed)

When `outdoorAirTemperature` drops below approximately -5 °C, condensate on the heat exchanger
can freeze. Professional units handle this by:
- Reducing or stopping intake fan while exhaust continues to warm the exchanger
- Using a pre-heater (electric or ground-source)

Implementation: periodically reduce intake PWM or stop it entirely when outdoor temperature is
below threshold, letting exhaust air defrost the exchanger. Resume normal operation when
`supplyAirTemperature` rises above a safe level.

### 3. Supply Air Humidity Monitoring

**Effort:** 1x SHT30 or SHT40 sensor (~80-100 CZK) in supply duct

Monitor humidity of air delivered to rooms. In winter, recuperation can over-dry indoor air.
With a humidity sensor in the supply duct, the system could:
- Warn when indoor humidity drops too low
- Adjust ventilation rate to balance air quality vs. humidity
- Consider enthalpy-based control (recovering both heat and moisture)

**Sensor choice:**
- SHT30: wider voltage range (2.15-5.5V), +-2% RH accuracy
- SHT40: more accurate (+-1.8% RH), requires 3.3V

### 4. Differential Pressure Sensor (Filter Monitoring)

**Effort:** 1x differential pressure sensor (BMP280 or MPXV7002)

Measure pressure drop across the filter — a more objective indicator of filter clogging than
the current time-based interval. When pressure drop exceeds a threshold, trigger filter
cleaning alert.

Optional: second sensor for room pressure to detect under/overpressure conditions.

### 5. VOC Sensor (Volatile Organic Compounds)

**Effort:** 1x SGP41 or SGP30 sensor (~150 CZK)

Professional units control ventilation not only by CO2 but also by VOC levels (paints,
cleaning products, cooking fumes). Add a `vocThreshold` alongside existing `co2Threshold`
values.

**Sensor choice:**
- SGP30: older, provides eCO2 + TVOC readings
- SGP41: newer, provides VOC and NOx index, lower power, more accurate

Integration: add `voc()` field to `HrvModule`, similar to existing `co2()`.

### 6. Dew Point-Based Bypass Control

**Effort:** Software + humidity sensor in duct (see item 3)

Instead of simple temperature comparison for bypass control, calculate the **dew point** from
temperature + humidity. Control bypass to prevent condensation on the heat exchanger.

This is more precise than the current temperature-only hysteresis approach.

### 7. Night Mode / Weekly Schedule

**Effort:** Software only

Automatic power reduction at night or based on schedule (nobody home = minimum ventilation).
Integration with OpenHAB presence detection (phone WiFi, motion sensors).

Could use OpenHAB's built-in time-of-day rules or a cron-based schedule in HrvModule.

### 8. Energy Balance Dashboard

**Effort:** Software only (uses existing SCT013 sensors)

Calculate and display:
- How much energy the recuperation saves (heat recovered)
- How much energy the fans consume
- Net energy savings over time
- ROI visualization

Data from existing `powerAd0`/`powerAd1` fields combined with duct temperatures.

## Gas Detection Module

**Effort:** ESP32 + MQ sensors (already purchased)

Sensors available:
- 3x MQ-5 (natural gas / methane) — mount near ceiling (methane is lighter than air)
- 3x MQ-6 (LPG / propane-butane) — mount near floor (propane is heavier than air)
- 2x MQ-2 (smoke / general flammable gases) — general purpose

Integration via ESPHome → MQTT → OpenHAB → existing `gas()` / `smoke()` fields in HrvModule.

**Note:** MQ sensors require 5V, ~150 mA continuous power per sensor (heated SnO2 element).
Use breakout modules for easy wiring. 24-48h initial burn-in required.
