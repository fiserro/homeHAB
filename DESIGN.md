# Design Document: Heat Recovery Ventilator (HRV) Rule

## Přehled

Tento dokument popisuje návrh systému pro automatické řízení rekuperace (Heat Recovery Ventilator - HRV) v OpenHAB prostředí pomocí Java223 automation engine. Systém agreguje data z různých senzorů a přepínačů a na jejich základě dynamicky řídí výkon ventilátoru rekuperace.

## Konvence pojmenování

- **HRV** (Heat Recovery Ventilator) - používá se v názvech všech HRV-specifických komponent
- **Hrv** - camelCase varianta pro Java třídy (např. `HrvConfig`, `HrvCalculator`)
- **AggregationType** - obecná třída pro agregaci, použitelná i pro jiné systémy (ne jen HRV)

## Business Požadavky

### Vstupy
- **Senzory vlhkosti** (Number) - měření vlhkosti v různých místnostech
- **Senzory CO2** (Number) - měření koncentrace CO2
- **Boolean přepínače**:
  - Detektor kouře
  - Stav digestoře (zapnuto/vypnuto)
  - Senzory otevřených oken
  - Manuální režim
  - Boost režim
  - Dočasný manuální režim
  - Dočasný boost režim
- **Manuální hodnota výkonu** (Number 0-100) - aktivní při manuálním režimu

### Výstup
- MQTT kanál s hodnotou 0-100 reprezentující výkon ventilátoru v procentech

### Klíčové požadavky
1. **Dynamická konfigurace** - možnost přidávat/odebírat zařízení bez rekompilace
2. **Agregace hodnot** - více senzorů stejného typu se agregují (MIN, MAX, SUM, COUNT)
3. **Reaktivita** - přepočet při změně kteréhokoliv vstupního kanálu
4. **Globální parametry** - konfigurovatelné časové limity pro dočasné režimy

## Architektura

### Vrstvová struktura

```
┌─────────────────────────────────────────────────┐
│   OpenHAB Configuration Layer                   │
│   (Items, MQTT bindings, Rule instantiation)    │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│   Rule Layer (HrvRule)                          │
│   - Event handling                              │
│   - Orchestration                               │
│   - Timer management                            │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│   Business Logic Layer                          │
│   - HrvCalculator                               │
│   - Input aggregation                           │
│   - Decision logic                              │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│   Domain Model Layer                            │
│   - HrvInputType enum                           │
│   - HrvConfig                                   │
│   - HrvState                                    │
│   - AggregationType enum (obecná třída)         │
└─────────────────────────────────────────────────┘
```

## Návrh tříd

### 1. Domain Model

#### HrvInputType Enum
- **Package**: `io.github.fiserro.homehab.hrv`
- **Účel**: Definuje všechny typy vstupů pro HRV systém
- **Vlastnosti**: Každý typ má datový typ (Boolean/Number), agregační funkci a defaultní výkon
- **Hlavní typy**:
  - Boolean modes: `MANUAL_MODE`, `TEMPORARY_MANUAL_MODE`, `BOOST_MODE`, `TEMPORARY_BOOST_MODE`
  - Boolean sensors: `SMOKE_DETECTOR`, `EXHAUST_HOOD`, `WINDOW_OPEN`
  - Number sensors: `HUMIDITY`, `CO2`
  - Manual control: `MANUAL_POWER`

#### AggregationType Enum
- **Package**: `io.github.fiserro.homehab`
- **Účel**: Obecná třída pro agregaci hodnot (použitelná i pro jiné systémy)
- **Typy**: `MIN`, `MAX`, `SUM`, `COUNT`
- **Podpora**: Number i Boolean hodnoty

#### HrvState
- **Package**: `io.github.fiserro.homehab.hrv`
- **Účel**: Reprezentace stavu HRV systému v daném okamžiku
- **Vlastnosti**: agregované vstupy, vypočtený výkon, aktivní režim, čas poslední aktualizace

### 2. Business Logic Layer

#### HrvConfig
- **Package**: `io.github.fiserro.homehab.hrv`
- **Účel**: Konfigurace HRV systému s prahovými hodnotami a výkony
- **Builder pattern**: Všechny parametry konfigurovatelné přes builder
- **Defaults**: Statická metoda `defaults()` vrací rozumné výchozí hodnoty
- **Parametry**:
  - Prahové hodnoty: `humidityThreshold`, `co2Threshold`
  - Výkony pro režimy: `smokePower`, `windowOpenPower`, `boostPower`, atd.
  - Časové limity: `temporaryModeTimeoutMinutes`

#### HrvCalculator
- **Package**: `io.github.fiserro.homehab.hrv`
- **Účel**: Výpočetní logika - rozhoduje o výkonu ventilátoru
- **Prioritní systém**:
  1. Bezpečnostní override (kouř, okna)
  2. Manuální režimy
  3. Boost režimy
  4. Digestoř
  5. Automatický režim (vlhkost, CO2)

### 3. Rule Layer

#### HrvRule
- **Package**: `io.github.fiserro.homehab.hrv`
- **Účel**: OpenHAB Rule s fluent builder API pro konfiguraci
- **Služby**: ScriptBusEvent, Scheduler, ItemRegistry (auto-injected)
- **Funkce**:
  - Custom fluent builder s `.input()` a `.output()` metodami
  - Načítání konfigurace z OpenHAB Items (`hrv_config_*` prefix)
  - Agregace vstupů podle typu
  - Timer management pro temporary režimy
  - Automatická reakce na změny konfigurace

## Integrace s OpenHAB

### Programová registrace s fluent API (Doporučeno)

Toto je nejčistší a nejčitelnější přístup s použitím fluent builder pattern.

#### Konfigurace v OpenHAB scriptu
```java
// File: $OPENHAB_CONF/automation/jsr223/hrv_config.java

import io.github.fiserro.homehab.hrv.*;
import helper.rules.annotations.Rule;

@Slf4j
public class HrvConfiguration {

    // OpenHAB services (auto-injected by Java223)
    private ScriptBusEvent events;
    private Scheduler scheduler;
    private ItemRegistry itemRegistry;

    private HrvRule hrvRule;

    @Rule(
        name = "hrv.config.init",
        description = "Initialize HRV control rule"
    )
    @StartupTrigger  // Runs once at startup
    public void initialize() {
        log.info("Initializing HRV control system");

        // Create HRV rule with fluent builder API
        hrvRule = HrvRule.builder()
            // Control modes
            .input(HrvInputType.MANUAL_MODE, "hrv_manual_mode")
            .input(HrvInputType.TEMPORARY_MANUAL_MODE, "hrv_temp_manual_mode")
            .input(HrvInputType.BOOST_MODE, "hrv_boost_mode")
            .input(HrvInputType.TEMPORARY_BOOST_MODE, "hrv_temp_boost_mode")
            .input(HrvInputType.MANUAL_POWER, "hrv_manual_power")

            // Safety inputs - multiple smoke detectors
            .input(HrvInputType.SMOKE_DETECTOR, "smoke_detector_living_room")
            .input(HrvInputType.SMOKE_DETECTOR, "smoke_detector_bedroom")

            // Window sensors - multiple windows
            .input(HrvInputType.WINDOW_OPEN, "window_living_room")
            .input(HrvInputType.WINDOW_OPEN, "window_bedroom")
            .input(HrvInputType.WINDOW_OPEN, "window_kitchen")

            // Exhaust hood
            .input(HrvInputType.EXHAUST_HOOD, "kitchen_exhaust_hood")

            // Environmental sensors - multiple humidity sensors
            .input(HrvInputType.HUMIDITY, "humidity_bathroom1")
            .input(HrvInputType.HUMIDITY, "humidity_bathroom2")
            .input(HrvInputType.HUMIDITY, "humidity_living_room")

            // CO2 sensors
            .input(HrvInputType.CO2, "co2_living_room")
            .input(HrvInputType.CO2, "co2_bedroom")

            // Output channel
            .output("hrv_output_power")

            // OpenHAB services
            .events(events)
            .scheduler(scheduler)
            .itemRegistry(itemRegistry)

            .build();

        log.info("HRV control system initialized successfully");
    }

    /**
     * Trigger method - called by Java223 when any registered item changes.
     * Java223 automatically binds this to all items registered via .input()
     */
    @Rule(
        name = "hrv.control",
        description = "HRV control triggered by sensor changes"
    )
    @ItemStateUpdateTrigger(itemName = "*")  // Will be dynamically bound to all input items
    public void onInputChanged(String itemName, String newValue) {
        if (hrvRule != null) {
            hrvRule.onInputChanged(itemName, newValue);
        }
    }
}
```

### Výhody fluent builder přístupu

1. **Explicitní a čitelné** - každý vstup na samostatném řádku, jasný účel
2. **Type-safe** - kompilátor kontroluje typy a enum hodnoty
3. **Self-documenting** - kód sám o sobě slouží jako dokumentace
4. **Snadná editace** - přidání/odebrání senzoru = jeden řádek
5. **Git-friendly** - změny jsou atomické, jasný diff
6. **IDE podpora** - auto-completion, refactoring, find usages
7. **Žádný boilerplate** - není potřeba `Map.of()`, `List.of()` nebo factory třídy

## Globální proměnné

### Řešení: OpenHAB Items jako konfigurace

Všechny konfigurační parametry včetně prahových hodnot a výkonů jsou reprezentovány jako standardní OpenHAB Items s prefixem `hrv_config_`:

```
// Global configuration items - Prahové hodnoty senzorů
Number hrv_config_humidity_threshold "Humidity Threshold [%d %%]" <humidity> { unit="%" }
Number hrv_config_co2_threshold "CO2 Threshold [%d ppm]" <co2> { unit="ppm" }

// Global configuration items - Výkony pro jednotlivé režimy
Number hrv_config_smoke_power "Smoke Detector Power [%d %%]" <smoke> { unit="%" }
Number hrv_config_window_open_power "Window Open Power [%d %%]" <window> { unit="%" }
Number hrv_config_manual_default_power "Manual Default Power [%d %%]" <settings> { unit="%" }
Number hrv_config_boost_power "Boost Mode Power [%d %%]" <fan> { unit="%" }
Number hrv_config_exhaust_hood_power "Exhaust Hood Power [%d %%]" <kitchen> { unit="%" }
Number hrv_config_humidity_power "High Humidity Power [%d %%]" <humidity> { unit="%" }
Number hrv_config_co2_power "High CO2 Power [%d %%]" <co2> { unit="%" }
Number hrv_config_base_power "Base Auto Power [%d %%]" <fan> { unit="%" }

// Global configuration items - Časové limity
Number hrv_config_temporary_timeout "Temporary Mode Timeout [%d min]" <time> { unit="min" }
```

#### Defaultní hodnoty:
Pokud Item neexistuje nebo nemá hodnotu, použijí se tyto defaultní hodnoty:
- `humidity_threshold`: 70%
- `co2_threshold`: 1000 ppm
- `smoke_power`: 100%
- `window_open_power`: 0%
- `manual_default_power`: 50%
- `boost_power`: 80%
- `exhaust_hood_power`: 60%
- `humidity_power`: 60%
- `co2_power`: 50%
- `base_power`: 30%
- `temporary_timeout`: 30 minut

#### Výhody tohoto přístupu:
1. **Žádná rekompilace** - změna parametrů bez restartu OpenHABu
2. **UI editace** - hodnoty lze měnit přes UI, sitemaps, habpanel
3. **Persistence** - hodnoty přežijí restart
4. **REST API** - programový přístup přes REST API
5. **Rules integration** - hodnoty lze měnit z dalších rules
6. **Live tuning** - možnost fine-tuningu thresholdů za běhu

#### Okamžitá reakce na změny

**Požadavek:** Systém musí reagovat na změny thresholds okamžitě (bez manuálního reload).

**Řešení:** Při změně kteréhokoliv `hrv_config_*` itemu se automaticky triggne `onInputChanged()`, který přenačte konfiguraci a přepočítá výstup:

```java
public void onInputChanged(String itemName, String newValue) {
    log.debug("Input changed: {} = {}", itemName, newValue);

    // Detect config change
    if (itemName.startsWith(CONFIG_PREFIX)) {
        log.info("Config parameter changed: {}", itemName);
        reloadConfiguration();
        return;
    }

    // Normal input handling
    HrvInputType inputType = findInputType(itemName);
    // ... rest of the method
}
```

**Registrace triggerů pro config items:**
```java
// Registrovat config items jako vstupy pro automatickou reload konfiguraci
hrvRule = HrvRule.builder()
    // ... vstupy ze senzorů ...

    // Config items - změna triggne reload konfigurace
    .input(HrvInputType.CONFIG_CHANGE, "hrv_config_humidity_threshold")
    .input(HrvInputType.CONFIG_CHANGE, "hrv_config_co2_threshold")
    .input(HrvInputType.CONFIG_CHANGE, "hrv_config_boost_power")
    // ... další config items ...

    .build();
```

**Poznámka**: Pro config items je potřeba přidat nový enum `HrvInputType.CONFIG_CHANGE`,
který bude speciálně zpracováván v `onInputChanged()` metodě.

**Výsledek:** Změna threshold → automatický trigger → reload config → přepočet výkonu → okamžitá reakce

#### Příklad Sitemap pro UI:
```
Frame label="HRV Configuration" {
    Text label="Sensor Thresholds" {
        Setpoint item=hrv_config_humidity_threshold minValue=40 maxValue=90 step=5
        Setpoint item=hrv_config_co2_threshold minValue=500 maxValue=2000 step=100
    }
    Text label="Power Levels" {
        Setpoint item=hrv_config_base_power minValue=10 maxValue=50 step=5
        Setpoint item=hrv_config_boost_power minValue=50 maxValue=100 step=10
        Setpoint item=hrv_config_humidity_power minValue=40 maxValue=80 step=5
        Setpoint item=hrv_config_co2_power minValue=40 maxValue=80 step=5
    }
    Text label="Safety Overrides" {
        Setpoint item=hrv_config_smoke_power minValue=80 maxValue=100 step=10
        Setpoint item=hrv_config_window_open_power minValue=0 maxValue=20 step=5
    }
    Text label="Timers" {
        Setpoint item=hrv_config_temporary_timeout minValue=10 maxValue=120 step=10
    }
}
```

## Deployment strategie

### Struktura projektu
```
homeHAB/
├── src/main/java/
│   ├── com/example/lib/
│   │   └── DelayedActions.java              # Existující knihovna
│   └── io/github/fiserro/homehab/
│       ├── AggregationType.java             # Obecná třída (ne HRV-specifická)
│       └── hrv/                             # HRV modul
│           ├── HrvInputType.java            # Input typy pro HRV
│           ├── HrvState.java                # Stav HRV systému
│           ├── HrvConfig.java               # Konfigurace HRV
│           ├── HrvCalculator.java           # Business logika
│           └── HrvRule.java                 # OpenHAB Rule s fluent builder
└── pom.xml
```

**Package konvence:**
- `io.github.fiserro.homehab` - obecné třídy (AggregationType)
- `io.github.fiserro.homehab.hrv` - HRV-specifické třídy
- `com.example.lib` - sdílené utility knihovny (DelayedActions)


### Build a deployment
```bash
# Build library JAR
mvn clean package

# Deploy library to OpenHAB
cp target/homeHAB-1.0-SNAPSHOT.jar $OPENHAB_CONF/automation/lib/java/

# Deploy configuration script
cp src/main/java/com/example/scripts/HrvConfiguration.java \
   $OPENHAB_CONF/automation/jsr223/hrv_config.java
```

**Poznámka:** Knihovna obsahuje HrvRule s fluent builder API. Konfigurační script
používá tento builder k vytvoření a registraci HRV rule instance.


## Budoucí rozšíření

### 1. Adaptivní kontrola
- Učení vzorců (machine learning)
- Predikce potřeby ventilace
- Optimalizace spotřeby energie

### 2. Multi-zónová ventilace
- Různé zóny s různými pravidly
- Koordinace mezi zónami

### 3. Externí integrace
- Weather API pro venkovní podmínky
- Kalendář/scheduling (nižší ventilace v noci)
- Notifikace při anomáliích

### 4. Dashboard a monitoring
- Grafana integrace
- Real-time monitoring
- Historické grafy

## Závěr

Tento design poskytuje robustní, flexibilní a rozšiřitelné řešení pro automatické řízení rekuperace v OpenHAB. Klíčové výhody:

- **Dynamická konfigurace zařízení** - žádná rekompilace při změně zařízení (přidání/odebrání senzorů)
- **Konfigurovatelné parametry** - všechny threshold hodnoty a výkony jako OpenHAB Items, měnitelné za běhu
- **Separace concerns** - business logika (HrvCalculator) oddělena od OpenHAB infrastruktury (HrvRule)
- **Testovatelnost** - vrstvy lze testovat nezávisle, mock-ovatelná konfigurace
- **Rozšiřitelnost** - snadné přidání nových sensorů, režimů a logiky
- **Maintainability** - čistý kód, jasná struktura, žádné hardcoded konstanty
- **Live tuning** - fine-tuning prahových hodnot v reálném čase bez restartu
- **Fluent API** - deklarativní konfigurace s fluent builder pattern
