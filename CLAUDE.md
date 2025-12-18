# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

homeHAB is an OpenHAB Java automation library built with JDK 21. The project provides reusable Java libraries for OpenHAB automation, with the primary focus on an HRV (Heat Recovery Ventilator) control system. The code is designed to run within OpenHAB's runtime environment and uses modern Java features.

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

**IMPORTANT**: This solution is currently a work in progress and not fully functional yet.

The development workflow follows these steps:

1. **Develop & Build** - Write code and build fat JAR with Maven
2. **Deploy to Dev** - Copy JAR and rules to local Docker OpenHAB
3. **Test & Debug** - Verify functionality using shared MQTT broker with real sensors
4. **Deploy to Prod** - When stable, deploy to production Raspberry Pi via SSH

### Shared MQTT Broker Architecture

The development and production environments share a common MQTT broker:
- **Mosquitto** runs on Raspberry Pi (`openhab.home:1883`)
- **Dev environment** connects to the same broker with prefix `homehab-dev/`
- **Prod environment** connects with prefix `homehab/`

**Benefit**: Development environment can use **real sensors** from production without needing to mock them. This enables realistic testing during development.

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
# Build and deploy to local Docker OpenHAB
./deploy.sh dev
```

This script:
1. Builds fat JAR with `mvn clean package`
2. Copies JAR to `openhab-dev/conf/automation/lib/java/`
3. OpenHAB automatically detects and loads the library

**Manual deployment:**
```bash
# Build the project
mvn clean package

# Copy fat JAR to shared directory
cp target/homeHAB-1.0-SNAPSHOT-shaded.jar openhab-dev/conf/automation/lib/java/

# Copy rule scripts
cp openhab-dev/conf/automation/jsr223/*.java openhab-dev/conf/automation/jsr223/
```

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
  - Client ID: `homehab-dev`
  - Topic prefix: `homehab-dev/`

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

The project is organized into two main packages:

1. **Library Code** (`src/main/java/io/github/fiserro/homehab/`)
   - `hrv/` - HRV (Heat Recovery Ventilator) control system
   - Reusable components meant to be deployed to OpenHAB's `automation/lib/java/` directory

2. **OpenHAB Scripts** (`openhab-dev/conf/automation/jsr223/`)
   - `HrvControlScript.java` - OpenHAB rule that instantiates and wires up HRV components
   - These extend `Java223Script` and use `@Rule` annotations
   - Deployed to OpenHAB's `automation/jsr223/` directory

### HRV System Architecture

The HRV system is designed as a layered architecture:

**Input Layer → Aggregation → Calculation → Output**

1. **HrvRule** (`HrvRule.java`)
   - Entry point for OpenHAB integration
   - Maps OpenHAB items to HRV input types using a fluent builder API
   - Aggregates multiple sensors per input type (e.g., multiple CO2 sensors)
   - Manages temporary mode timers (auto-off after configured timeout)
   - Handles configuration reloading when OpenHAB items change

2. **HrvCalculator** (`HrvCalculator.java`)
   - Pure calculation logic - no OpenHAB dependencies
   - Priority-based decision tree:
     - Priority 1: Safety (smoke detector, window open)
     - Priority 2: Manual modes (manual, temporary manual)
     - Priority 3: Boost modes (boost, temporary boost)
     - Priority 4: Exhaust hood
     - Priority 5: Automatic mode (humidity/CO2 sensors)

3. **HrvConfig** (`HrvConfig.java`)
   - Immutable configuration record with defaults
   - All thresholds and power levels are configurable

4. **HrvConfigLoader** (`HrvConfigLoader.java`)
   - Loads configuration from OpenHAB items (prefix: `hrvConfig`)
   - Falls back to defaults if items don't exist

5. **HrvInputType** (`HrvInputType.java`)
   - Enum defining all input types (sensors, modes)
   - Each type has data type (Boolean/Number) and aggregation strategy (OR/MAX)

6. **AggregationType** (`AggregationType.java`)
   - Defines aggregation strategies for multiple sensors of same type
   - OR: Any true → true (for booleans)
   - MAX: Highest value wins (for numbers)

### Key Design Patterns

**Builder Pattern for Rule Configuration:**
```java
HrvRule.builder()
    .input(HrvInputType.MANUAL_MODE, "Hrv_Manual_Mode")
    .input(HrvInputType.CO2, "Bedroom_CO2_Sensor")
    .input(HrvInputType.CO2, "Living_Room_CO2_Sensor")  // Multiple sensors aggregated
    .output("Hrv_Power_Output")
    .events(events)
    .scheduler(scheduler)
    .itemRegistry(itemRegistry)
    .build();
```

**Auto-Injection in OpenHAB Scripts:**
OpenHAB's Java223 engine automatically injects services into script fields:
- `ScriptBusEvent events` - For sending commands/updates to items
- `Scheduler scheduler` - For scheduling delayed actions
- `ItemRegistry itemRegistry` - For reading item states

**ScriptBusEvent API:**
The correct way to interact with OpenHAB items:
```java
events.sendCommand("itemName", "ON");
events.postUpdate("itemName", "100");
```

Do NOT use `ItemRegistry.get()` which returns the `Item` interface without a `send()` method.

**Rule Annotations:**
Scripts use annotations from `helper.rules.annotations`:
```java
@Rule(name = "rule.name", description = "Description")
@ItemStateChangeTrigger(itemName = "itemName")
public void onTrigger() { ... }
```

**Lombok for Boilerplate Reduction:**
All classes use Lombok annotations:
- `@Slf4j` - Automatic logger field
- `@RequiredArgsConstructor` - Constructor for final fields
- `@Builder` - Fluent builder pattern

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
# Generate all configuration (items + Zigbee)
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--sshHost=user@zigbee.home"

# Generate only items (no Zigbee)
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--zigbeeEnabled=false"

# Initialize items with default values (after OpenHAB restart)
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--initEnabled=true --habStateEnabled=false --outputEnabled=false --zigbeeEnabled=false"
```

### Generator Options

| Option | Default | Description |
|--------|---------|-------------|
| `--habStateEnabled` | `true` | Generate HabState items (input items + aggregation groups) |
| `--outputEnabled` | `true` | Generate output items |
| `--initEnabled` | `false` | Initialize items with default values via REST API |
| `--zigbeeEnabled` | `true` | Generate Zigbee Things and Items |
| `--sshHost` | - | SSH host for fetching Zigbee2MQTT devices (e.g., `user@host`) |
| `--mqttHost` | - | Direct MQTT host for fetching devices (alternative to SSH) |
| `--openhabUrl` | `http://localhost:8888` | OpenHAB REST API URL |
| `--outputDir` | `openhab-dev/conf` | Output directory for generated files |

### Fetching Zigbee Devices

The `ZigbeeGenerator` needs to fetch the device list from Zigbee2MQTT. There are two methods:

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

Generates OpenHAB items from `HabState.java` field annotations.

**Generated file:** `openhab-dev/conf/items/habstate-items.items`

**What it does:**
- Generates **input items** from `@InputItem` annotated fields (HRV control parameters)
- Generates **aggregation groups** from `@NumAgg` and `@BoolAgg` annotated fields
- Maps Java types to OpenHAB types (boolean → Switch, int → Number)
- Determines icons based on field names
- Input items belong to `gHrvInputs` group
- Aggregation groups are empty - Zigbee items should be manually assigned to them

### Output Items Generator (`OutputItemsGenerator`)

Generates output items from `HabState.java` fields annotated with `@OutputItem`.

**Generated file:** `openhab-dev/conf/items/output-items.items`

### Zigbee Generator (`ZigbeeGenerator`)

Generates OpenHAB Things and Items from Zigbee2MQTT devices.

**Generated files:**
- `openhab-dev/conf/things/mqtt.things` - MQTT broker configuration
- `openhab-dev/conf/things/zigbee-devices.things` - Thing definitions for each Zigbee device
- `openhab-dev/conf/items/zigbee-devices.items` - Items for all Zigbee device metrics

**Important:** Zigbee items are generated **without group assignments**. Users should manually assign items to aggregation groups defined in `habstate-items.items` based on their specific needs.

**Naming conventions:**
- **Things UID:** `mqtt:topic:zigbee2mqtt:zigbee_<ieee>`
- **Item name:** `mqttZigbee<Category>_<ieee>` (e.g., `mqttZigbeeHumidity_0x00158d008b8b7beb`)

### Items Initializer (`Initializer`)

Initializes OpenHAB items with default values from `HabState.builder().build()` via REST API.

**Usage:**
```bash
# After generating items and restarting OpenHAB:
mvn exec:java -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator" \
  -Dexec.args="--initEnabled=true --uiEnabled=false --outputEnabled=false --zigbeeEnabled=false"
```

**What it does:**
- Reads default values from `HabState.builder().build()`
- Sends HTTP PUT requests to OpenHAB REST API to set item states
- Initializes both `@InputItem` and `@OutputItem` annotated fields
- Reports success/failure for each item

**When to use:**
- After generating items for the first time
- After restarting OpenHAB with new items
- To reset all items to default values

## Item Naming Conventions

The project uses specific naming conventions for different types of items:

### Zigbee Items

Generated by `ZigbeeGenerator.java`:

- **Things UID:** `mqtt:topic:zigbee2mqtt:zigbee_<ieee>`
  - Example: `mqtt:topic:zigbee2mqtt:zigbee_0xa4c138aa8b540e22`
- **Item name:** `mqttZigbee<Category>_<ieee>`
  - Example: `mqttZigbeeSmoke_0xa4c138aa8b540e22`, `mqttZigbeeHumidity_0x00158d008b8b7beb`
- **Group assignment:** Items are generated without groups - manually assign to groups from `habstate-items.items`

### Input Items

Generated by `HabStateItemsGenerator.java`:

- **Item name:** `<fieldName>` (camelCase, matching HabState.java field names)
  - Example: `manualMode`, `humidityThreshold`, `co2ThresholdLow`, `basePower`
- **Group:** All items belong to `gHrvInputs` group

### Items File Structure

The project uses **three auto-generated items files**:

1. **`openhab-dev/conf/items/habstate-items.items`**
   - Input configuration parameters from `@InputItem` annotations
   - Aggregation groups from `@NumAgg` and `@BoolAgg` annotations
   - Auto-generated by `HabStateItemsGenerator.java`
   - Contains items like: `manualMode`, `humidityThreshold`, `powerLow`
   - Contains groups like: `humidity`, `smoke`, `temperature`
   - All input items belong to `gHrvInputs` group

2. **`openhab-dev/conf/items/zigbee-devices.items`**
   - ALL Zigbee device items organized by device
   - Auto-generated by `ZigbeeGenerator.java`
   - Items are NOT automatically assigned to groups
   - Contains items like: `mqttZigbeeSmoke_0xa4c138aa8b540e22`, `mqttZigbeeHumidity_0x00158d008b8b7beb`
   - Manually assign items to groups from `habstate-items.items` as needed

3. **`openhab-dev/conf/items/output-items.items`**
   - Output items from `HabState.java`
   - Auto-generated by `OutputItemsGenerator.java` from `HabState.java` `@OutputItem` fields
   - Contains items like: `hrvOutputPower`
   - All items belong to `gOutputs` group

**Important:**
- All files are auto-generated - do NOT edit manually
- Scripts generate on a clean slate - removed devices will be automatically removed
- Re-run generators after modifying `HabState.java` (adding/removing `@InputItem`, `@OutputItem`, `@NumAgg`, or `@BoolAgg` fields) or Zigbee devices
- Manually edit Zigbee items to assign them to aggregation groups

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
Group:Number:MAX gZigbeeHumidity "Humidity"
Group:Number:AVG gZigbeeTemperature "Temperature"
Group:Switch:OR(ON,OFF) gZigbeeSmoke "Smoke"
```

### How It Works

When member items change, OpenHAB runtime **automatically** recalculates the Group's state:

```
Group:Number:MAX gZigbeeHumidity "Humidity"

Number mqttZigbeeHumidity_sensorA (gZigbeeHumidity)  // state: 45
Number mqttZigbeeHumidity_sensorB (gZigbeeHumidity)  // state: 52

// gZigbeeHumidity.state = MAX(45, 52) = 52
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
Group gZigbeeHumidity "Humidity"  // state: NULL (no aggregation)
```

### Usage in This Project

The `HabStateItemsGenerator` creates Groups with aggregation functions based on annotations in `HabState.java`:

- `@NumAgg(NumericAggregation.MAX)` → `Group:Number:MAX`
- `@NumAgg(NumericAggregation.AVG)` → `Group:Number:AVG`
- `@BoolAgg(BooleanAggregation.OR)` → `Group:Switch:OR(ON,OFF)`

Groups are named after the field name in `HabState.java` (e.g., `humidity`, `smoke`, `temperature`).

**Manual group assignment:** After generating Zigbee items, manually edit `zigbee-devices.items` to assign specific items to aggregation groups:

```
// Before (auto-generated):
Number mqttZigbeeHumidity_0x00158d008b8b7beb "Humidity" <humidity> { channel="..." }

// After (manually edited):
Number mqttZigbeeHumidity_0x00158d008b8b7beb "Humidity" <humidity> (humidity) { channel="..." }
```

This allows UI pages to reference Group items (e.g., `humidity`) and display aggregated sensor values automatically.

## Common Pitfalls

### Package Name: `helper.rules.annotations` (plural)
The correct package for rule annotations is `helper.rules.annotations` NOT `helper.rules.annotation` (singular).

### Generated Classes Are Runtime-Only
Do NOT create stub classes for `helper.generated.*` - these are dynamically generated by OpenHAB at runtime and won't compile in the local Maven build.

### Scripts Are Excluded from Compilation
The Maven compiler excludes `**/scripts/**/*.java` because these files depend on OpenHAB's generated classes. They compile only within OpenHAB's runtime environment.

### Configuration Items Must Use Prefix
All HRV configuration items in OpenHAB must use the prefix `hrvConfig` in camelCase (e.g., `hrvConfigCo2Threshold`, `hrvConfigHumidityThreshold`). This prefix is used to detect configuration changes and trigger reloads.

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

## Project Documentation

- [Main UI Pages Guide](docs/MAIN-UI-PAGES.md) - How to create and manage pages in OpenHAB Main UI
