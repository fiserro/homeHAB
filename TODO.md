## ESP32 Panel
- [ ] Ověřit že HTTP fetch funguje (zkontrolovat logy)
- [ ] Odstranit debug logging po ověření funkčnosti

## Generovani mqtt.things a mqtt.items obsahuje hardcoded url string a device id
- [ ] Brat v potaz .env soubor
- [ ] Zvazit moznost .gitignore a generovat v build fazi
- [ ] zvazit moznost template


## MQTT

### Cílová struktura
```
homehab/
├── hrv/                      # HRV Bridge (Python na RPi)
│   ├── value/                # výstupní hodnoty (command only)
│   │   ├── power             # base power (0-100)
│   │   ├── intake            # intake power
│   │   ├── exhaust           # exhaust power
│   │   └── test              # test power (pro kalibraci)
│   ├── source/               # GPIO source mapping (bidirectional, retained)
│   │   ├── gpio18            # power|intake|exhaust|test|off
│   │   └── gpio19            # power|intake|exhaust|test|off
│   └── calibration/          # kalibrační tabulky (bidirectional, retained)
│       ├── gpio18            # JSON {"pwm%": voltage}
│       └── gpio19            # JSON {"pwm%": voltage}
├── panel/                    # ESP32 Panel
│   ├── command/              # příkazy z panelu do OpenHAB
│   │   ├── temporaryManualMode   # ON/OFF
│   │   ├── temporaryBoostMode    # ON/OFF
│   │   └── manualPower           # 0-100
│   └── status                # online/offline (birth/will)
├── state/                    # OpenHAB → Panel (stavy pro zobrazení)
│   ├── hrvOutputPower
│   ├── temperature
│   ├── airHumidity
│   ├── co2
│   ├── pressure
│   ├── manualMode
│   ├── temporaryBoostMode
│   ├── temporaryManualMode
│   ├── smoke
│   └── manualPower
└── zigbee2mqtt/              # Zigbee senzory (neměnit - spravuje Z2M)
    ├── <device>              # state JSON
    ├── <device>/set          # command
    └── bridge/devices        # seznam zařízení
```

### Co je potřeba změnit

#### HRV Bridge
| Současný | Nový |
|----------|------|
| `homehab/hrv/power/set` | `homehab/hrv/value/power` |
| `homehab/hrv/intake/set` | `homehab/hrv/value/intake` |
| `homehab/hrv/exhaust/set` | `homehab/hrv/value/exhaust` |
| `homehab/hrv/test/set` | `homehab/hrv/value/test` |
| `homehab/hrv/gpio18/source` | `homehab/hrv/source/gpio18` |
| `homehab/hrv/gpio19/source` | `homehab/hrv/source/gpio19` |
| `homehab/hrv/calibration/gpio18/table` | `homehab/hrv/calibration/gpio18` |
| `homehab/hrv/calibration/gpio19/table` | `homehab/hrv/calibration/gpio19` |

#### Panel commands
| Současný | Nový |
|----------|------|
| `homehab/panel/temporaryManualMode/command` | `homehab/panel/command/temporaryManualMode` |
| `homehab/panel/temporaryBoostMode/command` | `homehab/panel/command/temporaryBoostMode` |
| `homehab/panel/manualPower/command` | `homehab/panel/command/manualPower` |

#### OpenHAB → Panel states
| Současný | Nový |
|----------|------|
| `homehab/hrvOutputPower/state` | `homehab/state/hrvOutputPower` |
| `homehab/temperature/state` | `homehab/state/temperature` |
| `homehab/airHumidity/state` | `homehab/state/airHumidity` |
| `homehab/co2/state` | `homehab/state/co2` |
| `homehab/pressure/state` | `homehab/state/pressure` |
| `homehab/manualMode/state` | `homehab/state/manualMode` |
| `homehab/temporaryBoostMode/state` | `homehab/state/temporaryBoostMode` |
| `homehab/temporaryManualMode/state` | `homehab/state/temporaryManualMode` |
| `homehab/smoke/state` | `homehab/state/smoke` |
| `homehab/manualPower/state` | `homehab/state/manualPower` |

### Soubory k úpravě
- [x] `src/main/python/dac_bridge/__init__.py` - Python HRV bridge
- [x] `openhab-dev/conf/things/hrv-bridge.things` - OpenHAB MQTT thing
- [x] `openhab-dev/conf/things/panel-mqtt.things` - Panel commands thing
- [x] `openhab-dev/conf/rules/panel-mqtt.rules` - Panel state publishing
- [x] `openhab-dev/conf/automation/jsr223/PanelMqttBridge.java` - Panel state publishing (Java)
- [x] `esp32-panel/hrv-panel.yaml` - ESP32 panel firmware