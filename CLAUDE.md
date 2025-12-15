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

## Utility Scripts

### Generate All Configuration

The project includes a convenience script to generate all OpenHAB configuration files at once.

**Location:** `scripts/generate-all-config.sh`

**Usage:**
```bash
# Via SSH (recommended)
./scripts/generate-all-config.sh --ssh user@host

# Via MQTT
./scripts/generate-all-config.sh --mqtt-host zigbee.home
```

**What it does:**
1. Runs `generate-input-items.py` to generate input configuration items from `HabState.java`
2. Runs `generate-output-items.py` to generate output items from `HabState.java`
3. Runs `generate-zigbee-config.py` to generate Zigbee Things and Items
4. Displays summary of generated files

**After generation:**
```bash
# Restart OpenHAB to load configuration
docker-compose restart openhab

# Check logs
docker-compose logs -f openhab
```

---

### Zigbee Configuration Generator

The project includes a Python script for automatically generating OpenHAB Things and Items configuration from Zigbee2MQTT devices.

**Location:** `scripts/generate-zigbee-config.py`

**Usage:**
```bash
# Via SSH (recommended for remote Zigbee2MQTT)
python3 scripts/generate-zigbee-config.py --ssh user@host

# Via MQTT (requires mosquitto_sub)
python3 scripts/generate-zigbee-config.py --mqtt-host zigbee.home

# Custom output directory
python3 scripts/generate-zigbee-config.py --ssh user@host --output-dir path/to/conf
```

**What it does:**
- Fetches device list from Zigbee2MQTT via SSH or MQTT
- Generates `things/zigbee-devices.things` with MQTT Thing definitions for ALL Zigbee devices
- Generates `items/zigbee-devices.items` with ALL Zigbee items organized by metric category
- Automatically creates Group items for each metric category (temperature, humidity, smoke, etc.)
- Automatically maps Zigbee2MQTT exposes to OpenHAB channels
- Supports switches, sensors, numeric values, and enums
- Assigns appropriate icons based on metric category

**Generated files:**
- `openhab-dev/conf/things/zigbee-devices.things` - Thing definitions
- `openhab-dev/conf/items/zigbee-devices.items` - All Zigbee items with Groups

**Naming conventions:**
- **Things UID:** `mqtt:topic:zigbee:<ieee>` (e.g., `mqtt:topic:zigbee:0xa4c138aa8b540e22`)
- **Channel label:** `[zigbee, <category>]` (e.g., `[zigbee, smoke]`, `[zigbee, humidity]`)
- **Item name:** `mqttZigbee<Category>_<ieee>` (e.g., `mqttZigbeeSmoke_0xa4c138aa8b540e22`)
- **Group name:** `gZigbee<Category>` (e.g., `gZigbeeSmoke`, `gZigbeeHumidity`)

**After generation:**
```bash
# Review generated files
cat openhab-dev/conf/things/zigbee-devices.things
cat openhab-dev/conf/items/zigbee-devices.items

# Restart OpenHAB to load configuration
docker-compose restart openhab
```

**Important:**
- Generated files should NOT be edited manually
- Re-run the script whenever Zigbee devices are added/removed
- The script skips the Zigbee coordinator device automatically
- Generates on a clean slate - removed devices will be automatically removed from configuration

### Input Items Generator

The project includes a Python script for generating input items from `HabState.java` `@InputItem` annotations.

**Location:** `scripts/generate-input-items.py`

**Usage:**
```bash
# Generate input-items.items from HabState.java
python3 scripts/generate-input-items.py
```

**What it does:**
- Parses `HabState.java` and extracts all `@InputItem` annotated fields
- Generates `items/input-items.items` with appropriate item types (Switch for boolean, Number for int)
- Automatically determines icons based on field names (switch, energy, line, time, settings)
- Uses field names directly (camelCase)
- All items belong to `gHrvInputs` group

**Generated file:**
- `openhab-dev/conf/items/input-items.items` - Input configuration items

**Naming convention:**
- Item names use camelCase format matching field names: `<fieldName>`
- Examples: `manualMode`, `humidityThreshold`, `co2ThresholdLow`, `temporaryBoostModeDurationSec`

### Output Items Generator

The project includes a Python script for generating output items from `HabState.java` `@OutputItem` annotations.

**Location:** `scripts/generate-output-items.py`

**Usage:**
```bash
# Generate output-items.items from HabState.java
python3 scripts/generate-output-items.py
```

**What it does:**
- Parses `HabState.java` and extracts all `@OutputItem` annotated fields
- Generates `items/output-items.items` with appropriate item types
- Automatically maps Java types to OpenHAB types (int → Dimmer, boolean → Switch)
- Determines icons based on field names (energy, fan, settings)
- Uses field names directly (camelCase)
- All items belong to `gOutputs` group

**Generated file:**
- `openhab-dev/conf/items/output-items.items` - Output items with `gOutputs` group

**Naming convention:**
- Item names use camelCase format matching field names: `<fieldName>`
- Example: `hrvOutputPower`

## Item Naming Conventions

The project uses specific naming conventions for different types of items:

### Zigbee Items

Generated by `scripts/generate-zigbee-config.py`:

- **Things UID:** `mqtt:topic:zigbee:<ieee>`
  - Example: `mqtt:topic:zigbee:0xa4c138aa8b540e22`
- **Channel label:** `[zigbee, <category>]`
  - Example: `[zigbee, smoke]`, `[zigbee, humidity]`
- **Item name:** `mqttZigbee<Category>_<ieee>`
  - Example: `mqttZigbeeSmoke_0xa4c138aa8b540e22`, `mqttZigbeeHumidity_0x00158d008b8b7beb`
- **Group name:** `gZigbee<Category>`
  - Example: `gZigbeeSmoke`, `gZigbeeHumidity`, `gZigbeeTemperature`

### Input Items

Generated by `scripts/generate-input-items.py`:

- **Item name:** `<fieldName>` (camelCase, matching HabState.java field names)
  - Example: `manualMode`, `humidityThreshold`, `co2ThresholdLow`, `basePower`
- **Group:** All items belong to `gHrvInputs` group

### Items File Structure

The project uses **three auto-generated items files**:

1. **`openhab-dev/conf/items/zigbee-devices.items`**
   - ALL Zigbee device items organized by metric category
   - Auto-generated by `generate-zigbee-config.py`
   - Includes Group definitions for each category
   - Contains items like: `mqttZigbeeSmoke_0xa4c138aa8b540e22`, `mqttZigbeeHumidity_0x00158d008b8b7beb`

2. **`openhab-dev/conf/items/input-items.items`**
   - Input configuration parameters in camelCase
   - Auto-generated by `generate-input-items.py` from `HabState.java` `@InputItem` fields
   - Contains items like: `manualMode`, `humidityThreshold`, `co2ThresholdLow`
   - All labels prefixed with "HRV - "
   - All items belong to `gHrvInputs` group

3. **`openhab-dev/conf/items/output-items.items`**
   - Output items from `HabState.java`
   - Auto-generated by `generate-output-items.py` from `HabState.java` `@OutputItem` fields
   - Contains items like: `hrvOutputPower`
   - All items belong to `gOutputs` group

**Important:**
- All files are auto-generated - do NOT edit manually
- Scripts generate on a clean slate - removed devices will be automatically removed
- Re-run generators after modifying `HabState.java` (adding/removing `@InputItem` or `@OutputItem` fields) or Zigbee devices

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
