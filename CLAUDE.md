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
   - Loads configuration from OpenHAB items (prefix: `hrv_config_`)
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
- Generates `things/zigbee-devices.things` with MQTT Thing definitions
- Generates `items/zigbee-devices.items` with Item definitions
- Automatically maps Zigbee2MQTT exposes to OpenHAB channels
- Supports switches, sensors, numeric values, and enums

**Generated files:**
- `openhab-dev/conf/things/zigbee-devices.things` - Thing definitions
- `openhab-dev/conf/items/zigbee-devices.items` - Item definitions

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

### HRV Configuration Generator

The project includes a Python script for generating HRV configuration items from the `HrvConfig.java` interface.

**Location:** `scripts/generate-hrv-config.py`

**Usage:**
```bash
# Generate hrv-config.items from HrvConfig.java
python3 scripts/generate-hrv-config.py
```

**What it does:**
- Parses `HrvConfig.java` interface and extracts `@Option` methods
- Generates `items/hrv-config.items` with Number items for each config parameter
- Automatically categorizes items (thresholds, power levels, timeouts)
- Preserves proper formatting and units

**Generated file:**
- `openhab-dev/conf/items/hrv-config.items` - Configuration items with prefix `hrv_config_*`

## HRV Item Naming Convention

The HRV system uses a **unified item naming convention** with specific prefixes:

### Item Prefixes

1. **`hrv_item_*`** - Manual control items
   - `hrv_item_manual_mode` - Manual mode switch
   - `hrv_item_temporary_manual_mode` - Temporary manual mode
   - `hrv_item_boost_mode` - Boost mode switch
   - `hrv_item_temporary_boost_mode` - Temporary boost mode
   - `hrv_item_exhaust_hood` - Exhaust hood active switch
   - `hrv_item_manual_power` - Manual power level (0-100%)

2. **`hrv_zigbee_item_<ieee>_<channel>`** - Zigbee sensor items for HRV
   - `hrv_zigbee_item_0xa4c138aa8b540e22_smoke` - Smoke detector
   - `hrv_zigbee_item_0x00158d008b8b7beb_humidity` - Humidity sensor
   - Format uses lowercase IEEE address from Zigbee device
   - Unique per device - supports multiple sensors of same type

3. **`hrv_config_*`** - Configuration parameters (auto-generated from `HrvConfig.java`)
   - `hrv_config_humidity_threshold` - Humidity threshold
   - `hrv_config_co2_threshold` - CO2 threshold
   - `hrv_config_smoke_power` - Power level for smoke detection
   - `hrv_config_boost_power` - Power level for boost mode
   - etc.

4. **`hrv_output_*`** - Output items
   - `hrv_output_power` - HRV power output (0-100%)

### HRV Zigbee Mapping

The mapping between HRV inputs and Zigbee devices is defined in `openhab-dev/conf/hrv-zigbee-mapping.yaml`:

```yaml
# HRV Zigbee Device Mapping
# Maps Zigbee devices to HRV input items

# Sensors from Zigbee devices (use IEEE address)
smoke_detector: "0xa4c138aa8b540e22"
humidity_sensor: "0x00158d008b8b7beb"
# window_sensor: "0x..."  # Add when available
# co2_sensor: "0x..."     # Add when available

# Zigbee channel mappings
channel_mappings:
  smoke_detector: "smoke"
  humidity_sensor: "humidity"
  # window_sensor: "contact"
  # co2_sensor: "co2"
```

**Usage:**
1. Add Zigbee device IEEE address for each HRV input type
2. Specify the channel name to use from that device
3. Run `generate-zigbee-config.py` to generate items with proper naming

### Items File Structure

The HRV system uses **two auto-generated items files**:

1. **`openhab-dev/conf/items/zigbee-devices.items`**
   - HRV control items (manual switches, zigbee sensors, output)
   - Auto-generated by `generate-zigbee-config.py`
   - Contains ONLY HRV-specific items, NO raw Zigbee items

2. **`openhab-dev/conf/items/hrv-config.items`**
   - Configuration parameters
   - Auto-generated by `generate-hrv-config.py` from `HrvConfig.java`

**Important:**
- Both files are auto-generated - do NOT edit manually
- Raw Zigbee items (like `Zigbee_0X00158D008B8B7Beb_Voltage`) are NOT generated
- Only HRV-relevant items are included in the configuration
- Re-run generators after adding new Zigbee devices or config parameters

### OpenHAB Item Naming Rules

When generating or creating OpenHAB items, follow these rules:

1. **Item names CANNOT start with a digit**
   - ❌ `0xa4c13856c27757e5_temperature`
   - ✅ `hrv_zigbee_item_0xa4c13856c27757e5_temperature`
   - The generator automatically adds prefix if needed

2. **Item names are case-sensitive**
   - Use consistent casing (lowercase for generated items)

3. **Use descriptive prefixes**
   - Helps with wildcard triggers in rules (e.g., `hrv_item_*`)
   - Groups related items logically

### Rule Triggers with Wildcards

The `HrvControl.java` script uses wildcard triggers for item groups:

```java
@Rule(name = "hrv.item.changed", description = "Handle HRV manual control item changes")
@ItemStateChangeTrigger(itemName = "hrv_item_*")
public void onItemChanged(ItemStateChange eventInfo) {
    String itemName = eventInfo.getItemName();  // Get specific item name
    // Handle change...
}

@Rule(name = "hrv.zigbee.changed", description = "Handle HRV Zigbee sensor changes")
@ItemStateChangeTrigger(itemName = "hrv_zigbee_item_*")
public void onZigbeeItemChanged(ItemStateChange eventInfo) {
    // Handle Zigbee sensor changes...
}

@Rule(name = "hrv.config.changed", description = "Handle HRV config changes")
@ItemStateChangeTrigger(itemName = "hrv_config_*")
public void onConfigChanged(ItemStateChange eventInfo) {
    // Reload configuration...
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
All HRV configuration items in OpenHAB must use the prefix `hrv_config_` (e.g., `hrv_config_co2_threshold`). This prefix is used to detect configuration changes and trigger reloads.

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
