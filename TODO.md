# TODO

## Dynamická kalibrace PWM modulů

Přesun kalibračních tabulek z Python kódu do OpenHAB pro možnost kalibrace přes UI.

### Motivace

Aktuálně jsou kalibrační tabulky (PWM duty → výstupní napětí) hardcoded v `pwm_calibration.py`.
Pro kalibraci je nutné:
1. Ručně měřit napětí multimetrem
2. Editovat Python soubor
3. Re-deployovat na RPi

**Cíl:** Umožnit kalibraci přímo z OpenHAB UI bez nutnosti editace kódu.

### Architektura

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            OpenHAB                                       │
│  ┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐   │
│  │ Calibration UI  │────▶│ Calibration Rule │────▶│ MQTT Binding    │   │
│  │ (Main UI Page)  │     │ (jsr223 script)  │     │ (hrv-bridge.    │   │
│  └─────────────────┘     └──────────────────┘     │  things)        │   │
│                                                    └────────┬────────┘   │
│  Items:                                                     │            │
│  - calibrationMode (String)                                 │            │
│  - calibrationGpioPin (Number)                              │            │
│  - calibrationStep (Number)                                 │            │
│  - calibrationMeasuredVoltage (Number)                      │            │
│  - calibrationTableGpio18 (String/JSON)                     │            │
│  - calibrationTableGpio19 (String/JSON)                     │            │
└─────────────────────────────────────────────────────────────┼────────────┘
                                                              │
                                              MQTT (retained)
                                                              │
┌─────────────────────────────────────────────────────────────┼────────────┐
│                        hrv-bridge (Python)                  │            │
│  ┌──────────────────────┐     ┌─────────────────────────┐   │            │
│  │ CalibrationManager   │◀────│ MQTT Subscriber         │◀──┘            │
│  │ - tables: {18: {}, } │     │ - calibration/gpio18/   │                │
│  │ - update_table()     │     │ - calibration/gpio19/   │                │
│  │ - get_pwm_for_pct()  │     │ - calibration/mode      │                │
│  └──────────────────────┘     └─────────────────────────┘                │
│                                                                          │
│  ┌──────────────────────┐                                                │
│  │ CalibrationWorkflow  │  (aktivní pouze v calibration mode)            │
│  │ - start(gpio)        │                                                │
│  │ - set_raw_pwm(duty)  │  ◀── Nastaví PWM BEZ kalibrace                 │
│  │ - next_step()        │                                                │
│  │ - finish()           │  ──▶ Publikuje novou tabulku                   │
│  └──────────────────────┘                                                │
└──────────────────────────────────────────────────────────────────────────┘
```

### MQTT Topics

| Topic | Směr | Retained | Popis |
|-------|------|----------|-------|
| `homehab/hrv/calibration/gpio18/table` | bidirectional | ✓ | JSON kalibrační tabulka GPIO18 |
| `homehab/hrv/calibration/gpio19/table` | bidirectional | ✓ | JSON kalibrační tabulka GPIO19 |
| `homehab/hrv/calibration/mode` | OH→Bridge | ✗ | `off`, `measuring`, `apply` |
| `homehab/hrv/calibration/step` | Bridge→OH | ✗ | Aktuální PWM krok (0-100) |
| `homehab/hrv/calibration/status` | Bridge→OH | ✗ | Status message pro UI |

### JSON formát kalibrační tabulky

```json
{
  "0": 0.0,
  "3": 0.01,
  "4": 0.02,
  "5": 0.37,
  "6": 0.68,
  "7": 0.92,
  "8": 1.23,
  "9": 1.34,
  "10": 1.48,
  "15": 2.06,
  "20": 2.63,
  "25": 3.19,
  "30": 3.77,
  "35": 4.32,
  "40": 4.89,
  "45": 5.43,
  "50": 5.99,
  "55": 6.54,
  "60": 7.09,
  "65": 7.63,
  "70": 8.19,
  "75": 8.72,
  "80": 9.28,
  "85": 9.82,
  "90": 10.17,
  "95": 10.18,
  "100": 10.19
}
```

### Implementační kroky

- [ ] **1. Design & MQTT protokol** (tento dokument)

- [ ] **2. OpenHAB items (HrvModule.java)**
  ```java
  // Calibration control
  @InputItem @Option
  default String calibrationMode() { return "off"; }  // off | measuring

  @InputItem @Option
  default int calibrationGpioPin() { return 18; }  // 18 nebo 19

  @InputItem @Option
  default int calibrationStep() { return 0; }  // aktuální PWM krok

  @InputItem @Option
  default double calibrationMeasuredVoltage() { return 0.0; }

  // Calibration tables (JSON strings)
  @InputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:calibrationGpio18") @Option
  default String calibrationTableGpio18() { return "{}"; }

  @InputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:calibrationGpio19") @Option
  default String calibrationTableGpio19() { return "{}"; }

  // Read-only status from bridge
  @ReadOnlyItem @Option
  default String calibrationStatus() { return "idle"; }
  ```

- [ ] **3. MQTT Thing channels (hrv-bridge.things)**
  ```java
  // Add to existing Thing mqtt:topic:mosquitto:hrv_bridge

  // Calibration tables
  Type string : calibrationGpio18 "Calibration GPIO18" [
      commandTopic="homehab/hrv/calibration/gpio18/table",
      stateTopic="homehab/hrv/calibration/gpio18/table",
      retained=true
  ]
  Type string : calibrationGpio19 "Calibration GPIO19" [
      commandTopic="homehab/hrv/calibration/gpio19/table",
      stateTopic="homehab/hrv/calibration/gpio19/table",
      retained=true
  ]

  // Calibration control
  Type string : calibrationMode "Calibration Mode" [
      commandTopic="homehab/hrv/calibration/mode",
      stateTopic="homehab/hrv/calibration/mode"
  ]
  Type number : calibrationStep "Calibration Step" [
      stateTopic="homehab/hrv/calibration/step"
  ]
  Type string : calibrationStatus "Calibration Status" [
      stateTopic="homehab/hrv/calibration/status"
  ]
  ```

- [ ] **4. Python CalibrationManager (pwm_calibration.py)**
  ```python
  import json
  import logging

  log = logging.getLogger("hrv-bridge")

  class CalibrationManager:
      """Manages calibration tables with MQTT sync."""

      # Default calibration steps
      STEPS = [0, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, 35, 40,
               45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100]

      def __init__(self):
          # Start with hardcoded defaults
          self.tables = {
              18: PWM_CALIBRATION_GPIO18.copy(),
              19: PWM_CALIBRATION_GPIO19.copy(),
          }
          self.mqtt_loaded = {18: False, 19: False}

      def update_from_mqtt(self, gpio: int, json_str: str) -> bool:
          """Update calibration table from MQTT JSON payload."""
          try:
              data = json.loads(json_str)
              if not data:  # Empty JSON
                  return False
              self.tables[gpio] = {int(k): float(v) for k, v in data.items()}
              self.mqtt_loaded[gpio] = True
              log.info(f"Loaded calibration for GPIO{gpio}: {len(self.tables[gpio])} points")
              return True
          except (json.JSONDecodeError, ValueError) as e:
              log.error(f"Invalid calibration JSON for GPIO{gpio}: {e}")
              return False

      def get_pwm_for_percent(self, gpio: int, percent: float) -> float:
          """Get calibrated PWM duty cycle for desired output percent."""
          target_voltage = (percent / 100.0) * 10.0
          return self._pwm_for_voltage(target_voltage, self.tables[gpio])

      def export_json(self, gpio: int) -> str:
          """Export table as JSON for MQTT publish."""
          return json.dumps(self.tables[gpio], sort_keys=True)

      def _pwm_for_voltage(self, target_v: float, table: dict) -> float:
          """Inverse interpolation: find PWM duty for target voltage."""
          # ... (existing interpolation logic)
  ```

- [ ] **5. Python CalibrationWorkflow (__init__.py)**
  ```python
  class CalibrationWorkflow:
      """Handles step-by-step calibration measurement process."""

      def __init__(self, bridge: 'HrvBridge'):
          self.bridge = bridge
          self.active = False
          self.gpio = 18
          self.step_index = 0
          self.measurements = {}

      def start(self, gpio: int):
          """Start calibration for specified GPIO."""
          self.active = True
          self.gpio = gpio
          self.step_index = 0
          self.measurements = {}

          # Set raw PWM (no calibration) for first step
          step = CalibrationManager.STEPS[0]
          self._set_raw_pwm(step)
          self._publish_status(f"Měření: PWM {step}%")
          log.info(f"Calibration started for GPIO{gpio}")

      def _set_raw_pwm(self, duty: float):
          """Set PWM duty directly without calibration."""
          pin = self.bridge.output.pin_intake if self.gpio == 18 else self.bridge.output.pin_exhaust
          lgpio.tx_pwm(self.bridge.output.handle, pin, self.bridge.output.freq, duty)
          self.bridge.client.publish(
              f"{self.bridge.topic_prefix}/calibration/step",
              str(int(duty))
          )

      def record_and_next(self, voltage: float):
          """Record measurement and advance to next step."""
          step = CalibrationManager.STEPS[self.step_index]
          self.measurements[step] = voltage
          log.info(f"Recorded: PWM {step}% = {voltage:.2f}V")

          self.step_index += 1
          if self.step_index >= len(CalibrationManager.STEPS):
              self._finish()
          else:
              next_step = CalibrationManager.STEPS[self.step_index]
              self._set_raw_pwm(next_step)
              self._publish_status(f"Měření: PWM {next_step}%")

      def _finish(self):
          """Complete calibration and publish new table."""
          self.active = False

          # Publish new calibration table (retained)
          table_json = json.dumps(self.measurements, sort_keys=True)
          topic = f"{self.bridge.topic_prefix}/calibration/gpio{self.gpio}/table"
          self.bridge.client.publish(topic, table_json, retain=True)

          # Update local calibration manager
          self.bridge.calibration_manager.update_from_mqtt(self.gpio, table_json)

          # Reset to normal operation
          self._set_raw_pwm(0)
          self._publish_status("Kalibrace dokončena")
          log.info(f"Calibration complete for GPIO{self.gpio}: {len(self.measurements)} points")

      def cancel(self):
          """Cancel calibration and restore normal operation."""
          self.active = False
          self._set_raw_pwm(0)
          self._publish_status("Kalibrace zrušena")

      def _publish_status(self, msg: str):
          self.bridge.client.publish(
              f"{self.bridge.topic_prefix}/calibration/status",
              msg
          )
  ```

- [ ] **6. Python bridge MQTT integration (__init__.py)**
  ```python
  class HrvBridge:
      def __init__(self, ...):
          # ... existing init ...

          # Calibration
          self.calibration_manager = CalibrationManager()
          self.calibration_workflow = CalibrationWorkflow(self)

          # Calibration topics
          self.topic_calib_prefix = f"{self.topic_prefix}/calibration"

      def _on_connect(self, client, userdata, flags, rc):
          # ... existing subscriptions ...

          # Subscribe to calibration topics
          client.subscribe(f"{self.topic_calib_prefix}/gpio18/table")
          client.subscribe(f"{self.topic_calib_prefix}/gpio19/table")
          client.subscribe(f"{self.topic_calib_prefix}/mode")
          client.subscribe(f"{self.topic_calib_prefix}/measure")  # trigger from UI

      def _on_message(self, client, userdata, msg):
          topic = msg.topic
          payload = msg.payload.decode("utf-8").strip()

          # Calibration table updates
          if topic == f"{self.topic_calib_prefix}/gpio18/table":
              self.calibration_manager.update_from_mqtt(18, payload)
              return
          if topic == f"{self.topic_calib_prefix}/gpio19/table":
              self.calibration_manager.update_from_mqtt(19, payload)
              return

          # Calibration mode control
          if topic == f"{self.topic_calib_prefix}/mode":
              self._handle_calibration_mode(payload)
              return

          # Measurement trigger from UI
          if topic == f"{self.topic_calib_prefix}/measure":
              if self.calibration_workflow.active:
                  voltage = float(payload)
                  self.calibration_workflow.record_and_next(voltage)
              return

          # ... existing message handling ...

      def _handle_calibration_mode(self, mode: str):
          if mode == "off":
              if self.calibration_workflow.active:
                  self.calibration_workflow.cancel()
          elif mode.startswith("start:"):  # "start:18" or "start:19"
              gpio = int(mode.split(":")[1])
              self.calibration_workflow.start(gpio)
  ```

- [ ] **7. Update PwmOutput to use CalibrationManager**
  ```python
  class PwmOutput:
      def __init__(self, calibration_manager: CalibrationManager, ...):
          self.calibration = calibration_manager
          # ... rest of init ...

      def set_intake(self, percent: float):
          percent = max(0, min(100, percent))
          self.duty_intake = percent
          calibrated_duty = self.calibration.get_pwm_for_percent(18, percent)
          lgpio.tx_pwm(self.handle, self.pin_intake, self.freq, calibrated_duty)

      def set_exhaust(self, percent: float):
          percent = max(0, min(100, percent))
          self.duty_exhaust = percent
          calibrated_duty = self.calibration.get_pwm_for_percent(19, percent)
          lgpio.tx_pwm(self.handle, self.pin_exhaust, self.freq, calibrated_duty)
  ```

- [ ] **8. OpenHAB UI page (ui-pages.json)**
  - Nová stránka "Kalibrace HRV" nebo sekce v existující stránce
  - Komponenty:
    - Dropdown: GPIO pin (18/19)
    - Button: "Spustit kalibraci"
    - Label: Aktuální krok (PWM %)
    - Number input: Naměřené napětí (0-10V)
    - Button: "Uložit a další"
    - Button: "Zrušit"
    - Text area: Aktuální kalibrační tabulka (read-only JSON)

- [ ] **9. Persistence (mapdb.persist)**
  ```
  Items {
      calibrationTableGpio18 : strategy = restoreOnStartup
      calibrationTableGpio19 : strategy = restoreOnStartup
  }
  ```

- [ ] **10. End-to-end testing**
  - [ ] Start kalibrace z UI → bridge přepne do měřícího režimu
  - [ ] Zadání hodnoty → bridge posune na další krok
  - [ ] Dokončení → nová tabulka uložena v MQTT (retained)
  - [ ] Restart bridge → načte tabulku z MQTT
  - [ ] Restart OpenHAB → tabulka obnovena z MapDB
  - [ ] Fallback → bez MQTT dat použije hardcoded defaults

- [ ] **11. Dokumentace (docs/CALIBRATION.md)**
  - Popis účelu kalibrace
  - Hardware setup (multimetr, zapojení)
  - Krok-za-krokem průvodce UI
  - Troubleshooting
  - Reset na default hodnoty

### Fallback strategie

1. **Bridge startup:**
   - Načti hardcoded defaults z `pwm_calibration.py`
   - Připoj se k MQTT
   - Čekej na retained messages s kalibračními tabulkami
   - Pokud přijdou, přepiš defaults

2. **OpenHAB startup:**
   - MapDB obnoví `calibrationTableGpio18/19` z persistence
   - MQTT binding publikuje hodnoty → bridge je přijme

3. **Bez persistence:**
   - Bridge použije hardcoded defaults (vždy funkční)

### Bezpečnost

- Během kalibrace bridge ignoruje normální power příkazy
- Po dokončení/zrušení kalibrace se výstup resetuje na 0%
- UI zobrazí varování že kalibrace ovlivňuje HRV výkon

## ESP32 Panel
- [ ] Ověřit že HTTP fetch funguje (zkontrolovat logy)
- [ ] Odstranit debug logging po ověření funkčnosti

## Monitoring
- [ ] Zprovoznit Grafanu pro sledování item values v čase

## Automaticke zhasinani a rozsviceni panelu
- [x] Konfigurovatelny casovy udaj pro automaticke zhasnuti panelu pri necinnosti (substitution: screen_timeout)
- [x] Panel automaticky zhasne po urcenem casu (60s default)
- [x] Automaticke rozsviceni panelu po dotyku na obrazovku
