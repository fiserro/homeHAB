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
# Deploy homeHAB library to local dev environment
./deploy.sh local
```

This will:
1. Build the project (`mvn clean package`)
2. Copy the JAR to `openhab-dev/conf/automation/lib/java/`
3. OpenHAB will automatically detect and load the library

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

Deploy to production:

```bash
./deploy.sh
```

This uses the target from `.deploy-config` file (not in git).
