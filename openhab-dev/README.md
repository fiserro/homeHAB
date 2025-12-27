# OpenHAB Development Environment

This directory contains a clean OpenHAB development environment running in Docker.

## Structure

```
openhab-dev/
├── conf/           # OpenHAB configuration (version controlled)
│   └── automation/
│       ├── lib/java/     # Java library JARs (homeHAB)
│       └── jsr223/       # Java scripts and rules
├── addons/         # Additional bindings/addons (ignored by git)
└── userdata/       # Runtime data (ignored by git)
```

## Configuration

The dev environment uses `.env.dev` configuration file (via `.env` symlink).

**MQTT Settings:**
- Broker: `zigbee.home:1883`
- Client ID: `homehab-dev`
- Topic prefix: `homehab/`

## Starting the Environment

From the project root:

```bash
# Start OpenHAB
docker-compose up -d

# View logs
docker-compose logs -f openhab

# Stop OpenHAB
docker-compose down
```

## Accessing OpenHAB

- **Web UI**: http://localhost:8888
- **HTTPS**: https://localhost:8889
- **REST API**: http://localhost:8888/rest

## Deploying Library to Dev Environment

```bash
# Deploy homeHAB library to dev environment
./deploy.sh dev
```

This will:
1. Load configuration from `.env.dev`
2. Build the project (`mvn clean package`)
3. Copy the JAR to `openhab-dev/conf/automation/lib/java/`
4. OpenHAB will automatically detect and load the library

## Resetting the Environment

To get a completely clean OpenHAB instance:

```bash
# Stop and remove containers
docker-compose down

# Remove runtime data
rm -rf openhab-dev/userdata/*

# Start fresh
docker-compose up -d
```

## Environment Variables

Configuration is in `.env` file:

```bash
COMPOSE_PROJECT_NAME=homehab-dev      # Docker project name
OPENHAB_CONF=./openhab-dev/conf       # Configuration directory
OPENHAB_ADDONS=./openhab-dev/addons   # Addons directory
OPENHAB_USERDATA=./openhab-dev/userdata  # Runtime data
EXTRA_JAVA_OPTS=-Xms512m -Xmx1024m    # JVM options
```

## Production Environment

The production environment runs on a Raspberry Pi with OpenHABian.

**Configuration:**
1. Create `.env.prod` from the example:
   ```bash
   cp .env.prod.example .env.prod
   # Edit .env.prod with your production settings
   ```

2. Configure production settings:
   - MQTT broker: `zigbee.home:1883`
   - Client ID: `homehab-prod`
   - Topic prefix: `homehab/`
   - Deploy target: `openhabian@raspberrypi.local:/etc/openhab`

**Deploy to production:**

```bash
./deploy.sh prod
```

This will:
1. Load configuration from `.env.prod`
2. Build the project
3. Deploy via SSH/SCP to the Raspberry Pi
4. OpenHAB will automatically detect and load the library
