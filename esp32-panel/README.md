# ESP32-P4 HRV Control Panel

## Big Picture

Cílem je vytvořit dotykový ovládací panel pro HRV (Heat Recovery Ventilator) systém, který komunikuje s OpenHAB přes MQTT.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ARCHITEKTURA                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────┐         MQTT          ┌─────────────────────────┐ │
│  │  ESP32-P4 Panel     │◄──────────────────────►│  OpenHAB               │ │
│  │  (na zdi)           │    openhab.home:1883   │  (Raspberry Pi)        │ │
│  ├─────────────────────┤                        ├─────────────────────────┤ │
│  │  - 4" dotykový LCD  │                        │  - HrvControl.java     │ │
│  │  - Ethernet         │                        │  - Zigbee senzory      │ │
│  │  - LVGL UI          │                        │  - MQTT broker         │ │
│  │  - ESPHome firmware │                        │  - REST API            │ │
│  └─────────────────────┘                        └─────────────────────────┘ │
│                                                                             │
│  Panel ODEBÍRÁ z OpenHAB:          Panel PUBLIKUJE do OpenHAB:             │
│  ├── temperature/state              ├── panel/mode/command (auto/boost/manual)
│  ├── airHumidity/state              ├── panel/power/command (0-100)        │
│  ├── co2/state                      └── panel/status (online/offline)      │
│  ├── hrvOutputPower/state                                                  │
│  ├── manualMode/state                                                      │
│  └── boostMode/state                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Hardware

**Waveshare ESP32-P4-86-Panel-ETH-2RO**
- CPU: ESP32-P4 (dual-core RISC-V 400MHz) + ESP32-C6 (WiFi6/BT)
- Display: 4" IPS 720×720, 5-point capacitive touch (GT911)
- Connectivity: 10/100 Mbps Ethernet (IP101 PHY), WiFi6
- Memory: 32MB PSRAM + 32MB Flash
- Extensions: 2× relay (10A/250VAC), RS485
- Power: DC 6-30V

## Development Stack

| Komponenta | Popis |
|------------|-------|
| **ESPHome** | Firmware framework (YAML konfigurace) |
| **LVGL** | Graphics library pro UI |
| **MQTT** | Komunikace s OpenHAB |
| **ESP-IDF** | Underlying framework (automaticky přes ESPHome) |

## Struktura projektu

```
~/workspace/
├── homeHAB/                              # Hlavní projekt
│   └── esp32-panel/                      # ESP32 panel konfigurace
│       ├── README.md                     # Tato dokumentace
│       ├── hrv-panel.yaml                # ESPHome config pro reálný HW
│       ├── hrv-panel-host.yaml           # ESPHome config pro PC simulaci
│       ├── mqtt-simulator.py             # Python MQTT simulátor
│       ├── secrets.yaml                  # Credentials (gitignored)
│       └── secrets.yaml.example          # Template pro credentials
│
├── LVGL_Simulator/                       # LVGL desktop simulátor (nepoužíváme)
│   └── ...                               # Vyžaduje C++ reimplementaci UI
│
└── Waveshare-ESP32-P4-86-Panel-ETH-2RO/  # Reference projekt (ESPHome)
    └── src/                              # Ukázkové YAML konfigurace
        ├── main.yaml
        ├── pages/                        # LVGL stránky
        └── common/                       # Sdílené komponenty
```

## Instalované nástroje

```bash
# ESPHome (přes pipx s Python 3.12)
pipx install esphome --python $(brew --prefix python@3.12)/bin/python3.12

# SDL2 pro LVGL simulátor
brew install sdl2 cmake

# Python MQTT klient
pip3 install paho-mqtt
```

## Spuštění simulátorů

### 1. MQTT Simulátor (komunikace s OpenHAB)

Simuluje chování panelu - odebírá stavy z OpenHAB, posílá příkazy.

```bash
cd ~/workspace/homeHAB/esp32-panel
python3 mqtt-simulator.py
```

Příkazy:
- `auto`, `boost`, `manual` - odeslat příkaz módu
- `p <0-100>` - odeslat příkaz výkonu
- `s` - zobrazit aktuální stav (z OpenHAB)
- `q` - ukončit

### 2. LVGL Simulátor (NEPOUŽÍVÁME)

~~Desktop simulátor vyžaduje reimplementaci UI v C++, což je neudržovatelné.~~

Pro preview UI použij přímo ESPHome kompilaci a upload na reálný HW.

### 3. ESPHome Host (logika bez HW)

Testování ESPHome logiky na PC (bez MQTT - není podporováno na host).

```bash
cd ~/workspace/homeHAB/esp32-panel
esphome run hrv-panel-host.yaml
```

## ESPHome konfigurace

### Validace

```bash
cd ~/workspace/homeHAB/esp32-panel
esphome config hrv-panel.yaml
```

### Kompilace (bez uploadu)

```bash
esphome compile hrv-panel.yaml
```

### Upload na reálný HW

```bash
# První upload přes USB
esphome upload hrv-panel.yaml --device /dev/ttyUSB0

# Následné uploady přes OTA (WiFi/Ethernet)
esphome upload hrv-panel.yaml
```

### Logy

```bash
esphome logs hrv-panel.yaml
```

## MQTT Topics

### Panel odebírá (z OpenHAB)

| Topic | Popis |
|-------|-------|
| `homehab/temperature/state` | Agregovaná teplota |
| `homehab/airHumidity/state` | Agregovaná vlhkost |
| `homehab/co2/state` | Agregované CO2 |
| `homehab/hrvOutputPower/state` | Aktuální výkon HRV |
| `homehab/manualMode/state` | Stav manuálního módu |
| `homehab/boostMode/state` | Stav boost módu |

### Panel publikuje (do OpenHAB)

| Topic | Popis |
|-------|-------|
| `homehab/panel/status` | online/offline (birth/will) |
| `homehab/panel/mode/command` | auto/boost/manual |
| `homehab/panel/power/command` | 0-100 |

## Sledování MQTT zpráv

```bash
# Všechny zprávy z panelu
mosquitto_sub -h openhab.home -t "homehab/panel/#" -v

# Všechny zprávy v dev namespace
mosquitto_sub -h openhab.home -t "homehab/#" -v
```

## Další kroky

### Hotovo
- [x] Vytvořit LVGL UI pro HRV ovládání (teplota, vlhkost, slider, tlačítka)
- [x] Propojit LVGL s ESPHome konfigurací (`hrv-panel.yaml`)

### Krátkodobé
- [ ] Nakonfigurovat OpenHAB MQTT items pro příjem příkazů z panelu
- [ ] Otestovat na reálném HW (první upload přes USB)

### Dlouhodobé
- [ ] Přidat více stránek (nastavení, grafy, ...)
- [ ] Implementovat OTA updaty
- [ ] Přidat zvukovou signalizaci (ES8311 kodek)
- [ ] Přidat voice assistant (volitelné)

## Reference

- [Waveshare Wiki - ESP32-P4-86-Panel-ETH-2RO](https://www.waveshare.com/wiki/ESP32-P4-86-Panel-ETH-2RO)
- [ESPHome dokumentace](https://esphome.io/)
- [LVGL dokumentace](https://docs.lvgl.io/)
- [Reference projekt (alaltitov)](https://github.com/alaltitov/Waveshare-ESP32-P4-86-Panel-ETH-2RO)
- [LVGL Simulator template](https://github.com/chiefenne/LVGL_Simulator)

## Poznámky

### ESPHome Host limitace
- MQTT není podporováno na host platformě
- Pro testování MQTT použij `mqtt-simulator.py`

### LVGL Simulator
- **Nepoužíváme** - vyžaduje reimplementaci UI v C++, což je neudržovatelné
- Pro vývoj UI používáme přímo ESPHome + reálný HW

### Python verze
- ESPHome vyžaduje Python 3.12 (3.14 není podporován kvůli ruamel.yaml)
- paho-mqtt 1.6.1 (starší API bez CallbackAPIVersion)
