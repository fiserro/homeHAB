# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

homeHAB is an OpenHAB Java automation library built with JDK 21. The project provides reusable Java libraries for OpenHAB automation, with the primary focus on an HRV (Heat Recovery Ventilator) control system. The code is designed to run within OpenHAB's runtime environment and uses modern Java features.

## Important Rules

- **NEVER create git commits** - Only the user creates commits. Prepare changes but never run `git commit`.

## Prerequisites

- **Git** - Version control
- **Maven 3.x** - Build tool
- **Java JDK 21** - Eclipse Temurin 21 recommended
  - Already configured in `pom.xml` (`maven.compiler.source/target=21`)
  - Docker environment uses Temurin JDK 21 (see `Dockerfile`)

## Build Commands

### Quick build (skip tests and checks)
```bash
mvn clean package -DskipTests
```

### Compile the project
```bash
mvn clean compile
```

### Run tests
```bash
mvn test
```

### Run a single test
```bash
mvn test -Dtest=HrvConfigLoaderTest
```

### Package the library
```bash
mvn clean package
```

This creates two JARs in `target/`:
- `homeHAB-1.0-SNAPSHOT.jar` - Standard JAR
- `homeHAB-1.0-SNAPSHOT-shaded.jar` - Shaded JAR (includes only project classes, no dependencies)

### Useful Maven options
- `-DskipTests` - Skip unit tests
- `-U` - Force update of dependencies
- `-o` - Offline mode (skip dependency updates)

## Development Workflow

The development workflow follows these steps:

1. **Develop & Build** - Write code and build fat JAR with Maven
2. **Deploy to Dev** - Copy JAR and rules to local Docker OpenHAB
3. **Test & Debug** - Verify functionality using shared MQTT broker with real sensors
4. **Deploy to Prod** - When stable, deploy to production Raspberry Pi via SSH

### Shared MQTT Broker Architecture

The development and production environments share a common MQTT broker:
- **Mosquitto** runs on Raspberry Pi (`zigbee.home:1883`)
- **Both environments** use the same topic prefix `homehab/`
- **HRV Bridge** (Python on RPi) serves both dev and prod OpenHAB

**Benefit**: Development environment can use **real sensors** and **real HRV control** without needing to mock them. This is essential for testing hardware-dependent features like PWM calibration.

#### MQTT Client IDs

Each OpenHAB instance must use a unique `clientId` to connect to the same broker:
- **Production**: `clientid="homehab-prod"` (in `/etc/openhab/conf/things/mqtt.things`)
- **Development**: `clientid="homehab-dev"` (in `openhab-dev/conf/things/mqtt.things`)

If two clients connect with the same `clientId`, the broker disconnects the first one.

#### Topic Prefix vs Client ID

These are independent concepts:
- **Client ID** - Identifies the connection (prevents duplicate connections)
- **Topic prefix** - Namespace for messages (both use `homehab/`)

Since both use the same topic prefix, commands from either OpenHAB affect the real hardware.

#### Development Best Practice

When testing on dev that sends commands to HW (e.g., PWM calibration):
1. Set `controlEnabled=OFF` on production OpenHAB
2. This puts prod in read-only mode - calculations run but no commands are sent
3. After testing, set `controlEnabled=ON` to restore automatic control

The `controlEnabled` flag is in `CommonModule` and applies to all control systems (HRV, lights, heating, etc.). This prevents race conditions between dev and prod when both are connected to the same MQTT broker.

#### Optional Local Simulator

For testing edge cases (smoke alarm, high CO2) without real hardware, a local simulator is available:

```bash
# Start local MQTT broker and device simulator
docker-compose up -d mosquitto mqtt-simulator

# Change mqtt.things to use local broker
# host="mosquitto" instead of host="zigbee.home"

# Simulated devices publish to zigbee2mqtt/<device> topics
# Configuration: mqtt-simulator/devices.yaml
```

To switch back to production:
```bash
docker-compose stop mosquitto mqtt-simulator
# Change mqtt.things back to host="zigbee.home"
```

## Deployment

### Development Environment (Local Docker)

The dev environment runs OpenHAB in Docker using `docker-compose` and a custom `Dockerfile`.

**Directory Structure:**
```
openhab-dev/                    # Shared directory mounted into Docker
├── conf/
│   └── automation/
│       ├── lib/java/           # Fat JAR deployed here
│       │   └── homeHAB-*.jar
│       └── jsr223/             # Rule scripts deployed here
│           └── HrvControlScript.java
├── addons/                     # OpenHAB addons (gitignored)
└── userdata/                   # Runtime data (gitignored)
```

**Deployment process:**
```bash
# Build and deploy all components to local Docker OpenHAB
./scripts/deploy-all.sh dev

# Or deploy individual components:
./scripts/deploy-build.sh           # Maven build only
./scripts/deploy-generator.sh dev   # Run generator (items, things, UI)
./scripts/deploy-jar.sh dev         # Deploy JAR to OpenHAB
./scripts/deploy-ui-pages.sh dev    # Deploy UI pages
./scripts/deploy-panel.sh           # Compile ESP32 panel firmware
./scripts/deploy-restart.sh dev     # Restart services

# Skip panel or restart:
./scripts/deploy-all.sh dev --skip-panel
./scripts/deploy-all.sh dev --skip-restart
```

This process:
1. Builds fat JAR with `mvn clean package`
2. Runs generator for items, things, UI configs
3. Copies JAR to `openhab-dev/conf/automation/lib/java/`
4. Deploys UI pages
5. Compiles ESP32 panel firmware
6. Restarts OpenHAB

**Docker commands:**
```bash
# Start OpenHAB
docker-compose up -d

# View logs
docker-compose logs -f openhab

# Restart OpenHAB (to reload changes)
docker-compose restart openhab

# Stop OpenHAB
docker-compose down
```

**Dev environment configuration:**
- Configuration: `.env.dev` (symlinked via `.env`)
- Web UI: http://localhost:8888
- HTTPS: https://localhost:8889
- MQTT: `openhab.home:1883` (shared with prod)
  - Topic prefix: `homehab/`

### Production Environment (Remote OpenHABian)

Production runs on **Raspberry Pi** at `openhab.home` with OpenHABian.

**Deployment:**
```bash
# Deploy to production Raspberry Pi
./deploy.sh prod
```

This script:
1. Loads configuration from `.env.prod`
2. Builds fat JAR
3. Deploys via SSH/SCP to `openhab.home`
4. Copies to `/etc/openhab/automation/lib/java/` on RPi

**Production configuration:**
- Requires `.env.prod` file (see `.env.prod.example`)
- MQTT: `localhost:1883` (Mosquitto runs on same RPi)
  - Client ID: `homehab-prod`
  - Topic prefix: `homehab/`

### Manual Deployment
```bash
# Copy JAR to OpenHAB automation library directory
cp target/homeHAB-1.0-SNAPSHOT.jar /path/to/openhab/conf/automation/lib/java/
```

## Architecture

### Module Structure

The project uses a modular interface hierarchy based on the `io.github.fiserro:options` library:

```
src/main/java/io/github/fiserro/homehab/
├── module/
│   ├── CommonModule.java          # Base module: temperature, pressure, tickSecond
│   ├── HrvModule.java             # HRV control: modes, thresholds, power levels
│   └── FlowerModule.java          # Plant care: soil humidity
├── openhab/
│   └── OpenHabItemsExtension.java # Custom OptionsExtension for ItemRegistry
├── hrv/
│   └── HrvCalculator.java         # HRV calculation logic
├── HabStateFactory.java           # Factory for creating Options from OpenHAB items
└── annotations/                   # @InputItem, @OutputItem, @MqttItem, etc.

openhab-dev/conf/automation/
├── lib/java/
│   ├── HabState.java              # Interface extending all modules + MQTT specs
│   └── homeHAB-*.jar              # Shaded JAR with library code
└── jsr223/
    └── HrvControl.java            # OpenHAB rule script
```

### HabState Interface Hierarchy

HabState uses a modular design with self-referential type parameters:

```java
// Module interfaces in src/main/java (reusable library)
public interface CommonModule<T extends CommonModule<T>> extends Options<T> {
    @Option default int temperature() { return 20; }
}

public interface HrvModule<T extends HrvModule<T>> extends Options<T> {
    @InputItem @Option default boolean manualMode() { return false; }
    @OutputItem @Option default int hrvOutputPower() { return 50; }
}

// Home-specific interface in openhab-dev (MQTT bindings)
public interface HabState extends CommonModule<HabState>, HrvModule<HabState>, FlowerModule<HabState> {
    @Override @MqttItem(value = {"aqara*Temperature"}, numAgg = NumericAggregation.AVG)
    default int temperature() { return CommonModule.super.temperature(); }
}
```

**Key design principles:**
- **Modules are reusable** - `HrvModule` can be used in any home automation project
- **MQTT bindings are home-specific** - `HabState` adds `@MqttItem` to specify device patterns
- **Self-referential generics** - `withValue()` returns the correct type without casting
- **Immutability** - All state changes create new instances

### HRV System Architecture

The HRV system uses a functional data flow:

**OpenHAB Items → HabStateFactory → HrvCalculator → sendCommand**

1. **HabStateFactory** (`HabStateFactory.java`)
   - Creates `HabState` instances from OpenHAB item states
   - Uses `OpenHabItemsExtension` to load values
   - Writes output values back via `writeState()`

2. **OpenHabItemsExtension** (`OpenHabItemsExtension.java`)
   - Custom `OptionsExtension` that reads from OpenHAB items
   - Converts OpenHAB `State` to Java types (DecimalType → int, OnOffType → boolean)
   - Skips values already set in builder (prevents overwriting `withValue()` calls)

3. **HrvCalculator** (`HrvCalculator.java`)
   - Generic calculator accepting any `HrvModule<T>` implementation
   - Pure calculation logic - no OpenHAB dependencies
   - Priority-based decision tree:
     - Priority 1: Manual modes (manual, temporary manual) → `manualPower`
     - Priority 2: Boost mode (temporary boost) → `powerHigh`
     - Priority 3: Gas detection → `powerHigh`
     - Priority 4: Smoke detection → `POWER_OFF`
     - Priority 5: Humidity threshold → `powerHigh`
     - Priority 6: CO2 levels → `powerHigh/Mid/Low` based on thresholds
     - Default: `powerLow`

4. **HrvControl.java** (OpenHAB Script)
   - Triggers on item state changes
   - Creates `HabState` from current items
   - Calculates new output power
   - Sends command to output item

```java
// Example from HrvControl.java
@Rule(name = "item.changed", description = "Handle item changes")
@ItemStateChangeTrigger(itemName = "manualPower")
public void onZigbeeItemChanged() {
    HabState state = HabStateFactory.of(HabState.class, items);
    HabState calculated = new HrvCalculator<HabState>().calculate(state);
    events.sendCommand(_items.hrvOutputPower(), calculated.hrvOutputPower());
}
```

### HRV Bridge (Python)

The HRV Bridge is a Python service running on Raspberry Pi that receives MQTT commands and controls GPIO pins.

**Location:** `src/main/python/dac_bridge/`

**GPIO Pin Mapping:**
| GPIO | Function | Type | Description |
|------|----------|------|-------------|
| GPIO 4 | 1-Wire bus | Input | DS18B20 temperature sensors (outside temp) |
| GPIO 5 | Bypass valve | Digital | OFF=valve closed (air through exchanger), ON=valve open (bypass mode) |
| GPIO 12 | PWM output | PWM (HW) | Intake or exhaust fan (configurable via `sourceGpio18`) |
| GPIO 13 | PWM output | PWM (HW) | Intake or exhaust fan (configurable via `sourceGpio19`) |

**Note:** GPIO 17, 18, 22, 23 are reserved for Waveshare AD/DA board (SPI).

**MQTT Topics (OpenHAB → Bridge):**
| Topic | Type | Values | Description |
|-------|------|--------|-------------|
| `homehab/hrv/gpio17` | Switch | ON/OFF | Bypass valve control (mapped to GPIO 5) |
| `homehab/hrv/pwm/gpio18` | Number | 0-100 | PWM duty cycle (mapped to GPIO 12) |
| `homehab/hrv/pwm/gpio19` | Number | 0-100 | PWM duty cycle (mapped to GPIO 13) |

**OpenHAB Thing Configuration:** `openhab-dev/conf/things/hrv-bridge.things`

**Design Principle:** The Python bridge is hardware-focused - it only knows about GPIO pins and PWM values. All business logic (bypass, fan source selection, calibration) is handled in OpenHAB/Java. This keeps the bridge simple and reusable.

### Key Design Patterns

**Options Library Pattern:**
```java
// Create immutable state from OpenHAB items
HabState state = HabStateFactory.of(HabState.class, items);

// Calculate new values (returns new immutable instance)
HabState calculated = state.withValue("hrvOutputPower", 75);

// Access values
int power = calculated.hrvOutputPower();  // 75 (new value)
int humidity = calculated.airHumidity();  // from OpenHAB item
```

**Module Annotations:**
- `@Option` - Marks method as configurable option (from Options library)
- `@InputItem` - User-configurable input (generates OpenHAB item with `["user"]` tag)
- `@OutputItem` - Computed output (generates OpenHAB item with `["computed"]` tag)
- `@ReadOnlyItem` - System-managed read-only value
- `@MqttItem` - MQTT binding pattern with aggregation (e.g., `@MqttItem(value = "aqara*", numAgg = AVG)`)

**Auto-Injection in OpenHAB Scripts:**
OpenHAB's Java223 engine automatically injects services into script fields:
- `ScriptBusEvent events` - For sending commands/updates to items
- `Map<String, State> items` - Map of item names to current states
- `Items _items` - Generated type-safe item accessors

**ScriptBusEvent API:**
The correct way to interact with OpenHAB items:
```java
// Send command (triggers channels, rules)
events.sendCommand("itemName", "ON");
events.sendCommand(_items.hrvOutputPower(), 75);

// Post update (directly sets state, no channel trigger)
events.postUpdate("itemName", "100");
```

**Rule Annotations:**
Scripts use annotations from `helper.rules.annotations`:
```java
@Rule(name = "rule.name", description = "Description")
@ItemStateChangeTrigger(itemName = "itemName")
public void onTrigger() { ... }
```

**Lombok for Boilerplate Reduction:**
Library classes use Lombok annotations:
- `@Slf4j` - Automatic logger field

## Dependencies

### OpenHAB Dependencies
All OpenHAB dependencies use `provided` scope - they are available in the OpenHAB runtime:
- `org.openhab.core` (5.0.1)
- `org.openhab.automation.convenience-dependencies` (5.0.1)
- `org.openhab.automation.helper-lib` (5.0.1)

These artifacts are available from OpenHAB's Artifactory:
- Repository: https://openhab.jfrog.io/openhab/libs-release

### Third-Party Dependencies
- **Guava** (33.5.0-jre) - Used for `Multimap` in HrvRule
- **Options** (io.github.fiserro:options:1.0.0-alpha1) - Functional options library
- **Lombok** (1.18.34) - Annotation processing for boilerplate reduction

### Auto-Generated Helper Classes
The directory `openhab-dev/conf/automation/lib/java/helper/generated/` contains classes dynamically generated by OpenHAB:
- `Java223Script.java` - Base class for OpenHAB scripts
- `Items.java` - Type-safe item accessors
- `Actions.java` - OpenHAB action bindings

**NEVER create stub versions of these classes** - they are generated at runtime by OpenHAB.

## Docker Environment

### Custom OpenHAB Image
The project uses a custom Dockerfile (`Dockerfile`) that extends `openhab/openhab:5.0.0-debian`:
- Installs Temurin JDK 21 (required for Java223 script compilation)
- Debian Bookworm's default OpenJDK 17 is insufficient

### Directory Structure
```
openhab-dev/
├── conf/           # OpenHAB configuration (version controlled)
│   └── automation/
│       ├── lib/java/     # Library JARs (deployed by ./deploy.sh)
│       └── jsr223/       # Java scripts (HrvControlScript.java)
├── addons/         # Additional bindings (gitignored)
└── userdata/       # Runtime data (gitignored)
```

## Configuration Generators

All configuration is generated using a unified Java-based generator system. The `Generator` class orchestrates all generation tasks.

**Location:** `src/main/java/io/github/fiserro/homehab/generator/`

### Running the Generator

```bash
# Generate all configuration (items + MQTT/Zigbee)
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--sshHost=user@zigbee.home"

# Generate only items (no MQTT/Zigbee)
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--mqttEnabled=false"

# Initialize items with default values (after OpenHAB restart)
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--initEnabled=true --habStateEnabled=false --mqttEnabled=false"
```

### Generator Options

| Option | Default | Description |
|--------|---------|-------------|
| `--habStateEnabled` | `true` | Generate HabState items (input, output, aggregation groups) |
| `--initEnabled` | `false` | Initialize items with default values via REST API |
| `--mqttEnabled` | `true` | Generate MQTT/Zigbee Things and Items |
| `--sshHost` | - | SSH host for fetching Zigbee2MQTT devices (e.g., `user@host`) |
| `--mqttHost` | - | Direct MQTT host for fetching devices (alternative to SSH) |
| `--openhabUrl` | `http://localhost:8888` | OpenHAB REST API URL |
| `--outputDir` | `openhab-dev/conf` | Output directory for generated files |

### Fetching Zigbee Devices

The `MqttGenerator` needs to fetch the device list from Zigbee2MQTT. There are two methods:

**1. Via SSH (recommended):**
```bash
# Uses SSH to connect to the Zigbee2MQTT host and runs mosquitto_sub locally
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--sshHost=robertfiser@zigbee.home"
```
This method connects via SSH and executes `mosquitto_sub -h localhost -t 'zigbee2mqtt/bridge/devices' -C 1` on the remote host.

**2. Via direct MQTT connection:**
```bash
# Requires mosquitto_sub installed locally
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--mqttHost=zigbee.home"
```
This method requires `mosquitto-clients` package installed locally and direct network access to the MQTT broker.

### HabState Items Generator (`HabStateItemsGenerator`)

Generates OpenHAB items from `HabState` interface method annotations using the Options library.

**Generated file:** `openhab-dev/conf/items/habstate-items.items`

**What it does:**
- Uses `OptionsFactory.create(HabState.class)` to get all option definitions
- Iterates over `options()` to find annotated methods
- Generates **input items** from `@InputItem` annotated methods with `["user"]` tag
- Generates **output items** from `@OutputItem` annotated methods with `["computed"]` tag
- Generates **read-only items** from `@ReadOnlyItem` annotated methods with `["readonly", "computed"]` tags
- Generates **aggregation groups** from `@MqttItem` with `numAgg`/`boolAgg` parameters
- Maps Java types to OpenHAB types (boolean → Switch, int → Number/Dimmer)
- Determines icons based on method names
- Aggregation groups receive members automatically via `MqttGenerator` based on `@MqttItem` patterns

### MQTT Generator (`MqttGenerator`)

Generates OpenHAB MQTT Things and Items from Zigbee2MQTT devices.

**Generated files:**
- `openhab-dev/conf/things/mqtt.things` - MQTT broker configuration
- `openhab-dev/conf/things/mqtt-devices.things` - Thing definitions for each Zigbee device
- `openhab-dev/conf/items/mqtt-devices.items` - Items for all Zigbee device metrics

**Automatic group assignment:** Items are automatically assigned to aggregation groups based on `@MqttItem` patterns defined in `HabState.java`. The generator reads patterns from annotations and matches item names using wildcards.

**@MqttItem patterns:**
```java
// In HabState.java (method annotations with aggregation):
@Override @MqttItem(value = {"aqara*Humidity"}, numAgg = NumericAggregation.MAX)
default int airHumidity() { return HrvModule.super.airHumidity(); }

@Override @MqttItem(value = {"aqara*Temperature", "soil*Temperature"}, numAgg = NumericAggregation.AVG)
default int temperature() { return CommonModule.super.temperature(); }

@Override @MqttItem(value = "fire*Smoke", boolAgg = BooleanAggregation.OR)
default boolean smoke() { return HrvModule.super.smoke(); }

@Override @MqttItem(numAgg = NumericAggregation.MAX)  // Default: matches *Co2
default int co2() { return HrvModule.super.co2(); }
```

**Pattern syntax:**
- `*` - matches any characters (e.g., `aqara*Humidity` matches `aqara1Humidity`, `aqaraBedroomHumidity`)
- `?` - matches a single character
- Empty pattern - defaults to `*<FieldName>` (e.g., field `co2` matches `*Co2`)
- Multiple patterns can be specified as array or comma-separated

**Naming conventions:**
- **Things UID:** `mqtt:topic:zigbee2mqtt:zigbee_<ieee>`
- **Item name:** `<friendlyName><Category>` (e.g., `soil3Humidity`, `aqaraBedroomTemperature`)

### Items Initializer (`Initializer`)

Initializes OpenHAB items with default values from `OptionsFactory.create(HabState.class)` via REST API.

**Usage:**
```bash
# After generating items and restarting OpenHAB:
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--initEnabled=true --habStateEnabled=false --mqttEnabled=false"
```

**What it does:**
- Creates HabState instance using `OptionsFactory.create(HabState.class)`
- Reads default values from `options()` iteration (default method implementations)
- Sends HTTP PUT requests to OpenHAB REST API to set item states
- Initializes both `@InputItem` and `@OutputItem` annotated methods
- Reports success/failure for each item

**When to use:**
- After generating items for the first time
- After restarting OpenHAB with new items
- To reset all items to default values

## Item Naming Conventions

The project uses specific naming conventions for different types of items:

### MQTT/Zigbee Items

Generated by `MqttGenerator.java`:

- **Things UID:** `mqtt:topic:zigbee2mqtt:zigbee_<ieee>`
  - Example: `mqtt:topic:zigbee2mqtt:zigbee_0xa4c138aa8b540e22`
- **Item name:** `<friendlyName><Category>` (camelCase)
  - Example: `soil3Battery`, `livingRoomTemperature`, `bedroomHumidity`
- **Label:** `<FriendlyName readable> <Category>`
  - Example: `"Soil3 Battery"`, `"Living Room Temperature"`
- **Tags:** IEEE address, `mqtt`, `zigbee`
  - Example: `["0xa4c1384a743fbde0", "mqtt", "zigbee"]`
- **Group assignment:** Items are generated without groups - manually assign to groups from `habstate-items.items`

### Input Items

Generated by `HabStateItemsGenerator.java`:

- **Item name:** `<methodName>` (camelCase, matching HabState.java method names)
  - Example: `manualMode`, `humidityThreshold`, `co2ThresholdLow`, `powerLow`
- **Tags:** `["user"]` for input items, `["computed"]` for output items

### Items File Structure

The project uses **two auto-generated items files**:

1. **`openhab-dev/conf/items/habstate-items.items`**
   - Input configuration parameters from `@InputItem` method annotations
   - Output items from `@OutputItem` method annotations
   - Read-only items from `@ReadOnlyItem` method annotations
   - Aggregation groups from `@MqttItem` with `numAgg`/`boolAgg` parameters
   - Auto-generated by `HabStateItemsGenerator.java`
   - Contains items like: `manualMode`, `humidityThreshold`, `powerLow`, `hrvOutputPower`
   - Contains aggregation groups like: `airHumidity`, `smoke`, `temperature`

2. **`openhab-dev/conf/items/mqtt-devices.items`**
   - ALL MQTT/Zigbee device items organized by device
   - Auto-generated by `MqttGenerator.java`
   - Items are automatically assigned to groups based on `@MqttItem` patterns
   - Item names use friendlyName + category: `soil3Battery`, `livingRoomTemperature`
   - Each item has tags: IEEE address, `mqtt`, `zigbee`

**Important:**
- All files are auto-generated - do NOT edit manually
- Scripts generate on a clean slate - removed devices will be automatically removed
- Re-run generators after modifying `HabState.java` (adding/removing `@InputItem`, `@OutputItem`, `@MqttItem` methods) or Zigbee devices

### OpenHAB Item Naming Rules

When generating or creating OpenHAB items, follow these rules:

1. **Item names CANNOT start with a digit**
   - ❌ `0xa4c13856c27757e5_temperature`
   - ✅ `mqttZigbeeTemperature_0xa4c13856c27757e5`
   - The generator automatically adds prefix if needed

2. **Item names are case-sensitive**
   - Use consistent casing conventions per type
   - Zigbee items: camelCase with prefix (e.g., `mqttZigbeeSmoke_0xa4...`)
   - HRV config: camelCase with prefix (e.g., `hrvConfigHumidityThreshold`)

3. **Use descriptive prefixes**
   - Helps with wildcard triggers in rules (e.g., `mqttZigbeeSmoke_*`, `hrvConfig*`)
   - Groups related items logically
   - Makes it clear what system the item belongs to

### Rule Triggers with Wildcards

Rules can use wildcard triggers to handle multiple items:

```java
@Rule(name = "hrv.config.changed", description = "Handle HRV config changes")
@ItemStateChangeTrigger(itemName = "hrvConfig*")
public void onConfigChanged(ItemStateChange eventInfo) {
    String itemName = eventInfo.getItemName();  // Get specific item name
    // Reload configuration...
}

@Rule(name = "zigbee.smoke.changed", description = "Handle smoke detector changes")
@ItemStateChangeTrigger(itemName = "mqttZigbeeSmoke_*")
public void onSmokeDetectorChanged(ItemStateChange eventInfo) {
    // Handle smoke detector changes...
}
```

**Important:**
- Extract actual item name using `eventInfo.getItemName()`
- Wildcard triggers work with consistent naming prefixes
- Ignore output items in handlers to prevent feedback loops

## OpenHAB Group Aggregation

OpenHAB Groups can automatically aggregate values from their member items. This is configured in the Group definition syntax.

### Syntax

```
Group:BaseType:AggregationFunction groupName "Label"
```

Example:
```
Group:Number:MAX humidity "Humidity"
Group:Number:AVG temperature "Temperature"
Group:Switch:OR(ON,OFF) smoke "Smoke"
```

### How It Works

When member items change, OpenHAB runtime **automatically** recalculates the Group's state:

```
Group:Number:MAX humidity "Humidity"

Number bedroomHumidity (humidity)     // state: 45
Number livingRoomHumidity (humidity)  // state: 52

// humidity.state = MAX(45, 52) = 52
```

### Available Aggregation Functions

| Function | Type | Description |
|----------|------|-------------|
| `AVG` | Number | Average of all member values |
| `MAX` | Number | Maximum value |
| `MIN` | Number | Minimum value |
| `SUM` | Number | Sum of all values |
| `COUNT` | Number | Number of members |
| `OR(ON,OFF)` | Switch | ON if any member is ON |
| `AND(ON,OFF)` | Switch | ON only if all members are ON |
| `NAND(ON,OFF)` | Switch | ON if any member is OFF |
| `NOR(ON,OFF)` | Switch | ON only if all members are OFF |

### Without Aggregation

A Group without aggregation function has `NULL` state:
```
Group humidity "Humidity"  // state: NULL (no aggregation)
```

### Usage in This Project

The `HabStateItemsGenerator` creates Groups with aggregation functions based on `@MqttItem` annotations in `HabState.java`:

- `@MqttItem(numAgg = NumericAggregation.MAX)` → `Group:Number:MAX`
- `@MqttItem(numAgg = NumericAggregation.AVG)` → `Group:Number:AVG`
- `@MqttItem(boolAgg = BooleanAggregation.OR)` → `Group:Switch:OR(ON,OFF)`

Groups are named after the method name in `HabState.java` (e.g., `airHumidity`, `smoke`, `temperature`).

**Automatic group assignment:** Items are automatically assigned to groups based on `@MqttItem` patterns in `HabState.java`:

```java
// In HabState.java:
@Override @MqttItem(value = {"aqara*Humidity"}, numAgg = NumericAggregation.MAX)
default int airHumidity() { return HrvModule.super.airHumidity(); }
```

```
// Generated mqtt-devices.items:
Number aqaraBedroomHumidity "Aqara Bedroom Humidity" <humidity> (airHumidity) ["0x00158d008b8b7beb", "mqtt", "zigbee"] { channel="..." }
```

This allows UI pages to reference Group items (e.g., `airHumidity`) and display aggregated sensor values automatically.

## Common Pitfalls

### Package Name: `helper.rules.annotations` (plural)
The correct package for rule annotations is `helper.rules.annotations` NOT `helper.rules.annotation` (singular).

### Generated Classes Are Runtime-Only
Do NOT create stub classes for `helper.generated.*` - these are dynamically generated by OpenHAB at runtime and won't compile in the local Maven build.

### Scripts Are Excluded from Compilation
The Maven compiler excludes `**/scripts/**/*.java` because these files depend on OpenHAB's generated classes. They compile only within OpenHAB's runtime environment.

### SLF4J Logging in Scripts
SLF4J logging doesn't work in OpenHAB scripts. Use `System.out.println()` for debugging instead.

### Channel Links and Autoupdate
If an item has an invalid channel link (e.g., to a deleted Thing), OpenHAB's autoupdate may override your commands with predicted values. Delete invalid channel links in the UI.

## Language Guidelines

**Code and Documentation:**
- All code comments, JavaDoc, design documents (*.md), and technical documentation MUST be written in **English**
- This includes:
  - JavaDoc comments (`/** ... */`)
  - Inline code comments (`//`)
  - Design documents (DESIGN.md, README.md, etc.)
  - Commit messages
  - Variable names, method names, class names

**Communication:**
- Czech language is used ONLY for direct communication with the user
- When discussing code or design, use Czech for explanations but keep code examples in English

## References

- OpenHAB Documentation: https://www.openhab.org/docs/
- OpenHAB Scheduler API: https://www.openhab.org/javadoc/latest/org/openhab/core/scheduler/scheduler
- OpenHAB JSR223 Documentation: https://www.openhab.org/docs/configuration/jsr223.html

## OpenHAB Source Code (Local Repositories)

The following OpenHAB source code repositories are available locally for reference:

### openhab-core
**Location:** `/Users/robertfiser/workspace/openhab-core`

Key files:
- `bundles/org.openhab.core.automation.module.script/src/main/java/org/openhab/core/automation/module/script/defaultscope/ScriptBusEvent.java` - Interface for `events` object in scripts
- `bundles/org.openhab.core.automation.module.script/src/main/java/org/openhab/core/automation/module/script/internal/defaultscope/ScriptBusEventImpl.java` - Implementation of ScriptBusEvent
- `bundles/org.openhab.core/src/main/java/org/openhab/core/items/events/ItemEventFactory.java` - Factory for creating item events (command, state, stateChanged, etc.)

### openhab-addons-java223
**Location:** `/Users/robertfiser/workspace/openhab-addons-java223`

Key files:
- `bundles/org.openhab.automation.java223/src/main/java/org/openhab/automation/java223/internal/strategy/ScriptWrappingStrategy.java` - Script wrapping and auto-injected fields (`events`, `items`, `ir`, etc.)
- `bundles/org.openhab.automation.java223/src/helper/java/helper/rules/annotations/` - Rule annotations (`@Rule`, `@ItemStateChangeTrigger`, etc.)
- `bundles/org.openhab.automation.java223/src/helper/java/helper/rules/eventinfo/` - Event info classes (`ItemStateChange`, `ChannelEvent`, etc.)

### ScriptBusEvent API Notes

Both `sendCommand` and `postUpdate` publish events to the event bus - there is no "silent update":
- `sendCommand` → `ItemEventFactory.createCommandEvent()` → triggers command handlers and potentially state change
- `postUpdate` → `ItemEventFactory.createStateEvent()` → triggers state update and potentially state change

To avoid triggering `@ItemStateChangeTrigger` in a loop:
1. Check if value changed before updating: `if (!Objects.equals(oldValue, newValue))`
2. Filter output items in the handler: `if (eventInfo.getItemName().equals("outputItem")) return;`

Note: `ItemEventFactory.createStateEvent()` supports a `source` parameter, but `ScriptBusEvent.postUpdate()` doesn't expose it ([issue #1618](https://github.com/openhab/openhab-core/issues/1618)).

## Project Documentation

- [Main UI Pages Guide](docs/MAIN-UI-PAGES.md) - How to create and manage pages in OpenHAB Main UI
